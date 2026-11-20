package com.jetsport.ft10csync

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.jetsport.ft10csync.presentation.JetSportComposeHost
import com.jetsport.ft10csync.presentation.JetSportTheme
import com.jetsport.ft10csync.data.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.jetsport.ft10csync.ble.Ft10cScanCoordinator
import com.jetsport.ft10csync.ble.BluetoothStateMonitor
import com.jetsport.ft10csync.ble.BleUiEvent
import com.jetsport.ft10csync.ble.BleConnectionStateStore
import com.jetsport.ft10csync.presentation.AppAction
import com.jetsport.ft10csync.presentation.AppActionCoordinator
import com.jetsport.ft10csync.presentation.DashboardViewModel
import com.jetsport.ft10csync.presentation.DashboardScreenController
import com.jetsport.ft10csync.presentation.HistoryViewModel
import com.jetsport.ft10csync.presentation.ScreenLifecycleCoordinator
import com.jetsport.ft10csync.presentation.ScreenStateStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
    private val devices = linkedMapOf<String, BluetoothDevice>()
    private val deviceListStateStore = com.jetsport.ft10csync.presentation.DeviceListStateStore()
    private var pendingExportLogContent: String? = null
    private val exportLogRequestCode = 7401
    private var connectionWarningText by mutableStateOf<String?>(null)
    private var todayComposeState by mutableStateOf<com.jetsport.ft10csync.presentation.TodayScreenState?>(null)
    private var historyComposeState by mutableStateOf(com.jetsport.ft10csync.presentation.HistoryUiState())
    private var deviceComposeState by mutableStateOf(com.jetsport.ft10csync.presentation.DeviceUiState())
    private lateinit var screenLifecycle: ScreenLifecycleCoordinator
    private var appLanguage by mutableStateOf("ru")
    private var appDarkTheme by mutableStateOf(false)
    private var appDateFormat by mutableStateOf("DMY")
    private var phoneFinderAlertActive by mutableStateOf(false)
    private var phoneFinderSoundEnabled by mutableStateOf(true)
    private var phoneFinderVibrationEnabled by mutableStateOf(true)
    private var phoneFinderRingtoneLabel by mutableStateOf("System default")
    private val phoneFinderRingtonePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val picked: Uri? = getPickedRingtoneUri(data)
            settingsRepository.phoneFinderRingtoneUri = picked?.toString() ?: "__SILENT__"
            refreshPhoneFinderRingtoneLabel()
            log("PHONE FINDER: ringtone selection updated (${phoneFinderRingtoneLabel})")
        }
    }
    private lateinit var dashboardViewModel: DashboardViewModel
    private lateinit var dashboardController: DashboardScreenController
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var appActionCoordinator: AppActionCoordinator
    private var deviceScanning = false
    // Active bracelet day is domain state, not derived from the phone calendar.
    private var selected: BluetoothDevice? = null
    private var selectedAddress: String? = null
    // Connection lifecycle and GATT session coordination live outside Activity.
    private val bleConnectionState: BleConnectionStateStore get() = appCore.bleConnectionState
    private val connectedAddress: String? get() = bleConnectionState.connectedAddress
    // The BLE service owns AppCore lifetime; this Activity binds to and observes the shared state.
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var appCore: AppCore
    private lateinit var appCoreViewModel: AppCoreViewModel
    private var bleService: Ft10cBleForegroundService? = null
    private var serviceBound = false
    private var pendingConnectDevice: BluetoothDevice? = null
    private var pendingNotificationConnectDevice: BluetoothDevice? = null
    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? Ft10cBleForegroundService.LocalBinder ?: return
            val service = localBinder.service()
            bleService = service
            service.attachCore(appCore)
            appCoreViewModel.transferOwnershipToService()
            log("BLE foreground service took ownership of the runtime")
            pendingConnectDevice?.also { device ->
                pendingConnectDevice = null
                beginGattConnection(device)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            log("BLE foreground service disconnected from Activity")
        }
    }
    private val measurementRepository get() = appCore.measurementRepository
    private val settingsRepository get() = appCore.settingsRepository
    private val userProfileRepository get() = appCore.userProfileRepository
    private val activeDayRepository get() = appCore.activeDayRepository
    private val timeSyncState get() = appCore.timeSyncState
    private val syncCoordinator get() = appCore.syncCoordinator
    private val technicalLogStore get() = appCore.technicalLogStore
    private val adapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private var bluetoothUnavailableReason: String? = null
    private val bluetoothStateMonitorDelegate = lazy { BluetoothStateMonitor(this, ::onBluetoothAdapterAvailabilityChanged, ::log) }
    private val bluetoothStateMonitor: BluetoothStateMonitor by bluetoothStateMonitorDelegate

    // The registry currently contains FT-10C only. Other models can be added
    // without changing the common scanner or enabling unverified commands.
    private val deviceProfileRegistry get() = appCore.deviceProfileRegistry

    private val scanCoordinatorDelegate = lazy {
        Ft10cScanCoordinator(
            adapter = adapter ?: error("BluetoothAdapter is unavailable"),
            handler = handler,
            profileRegistry = deviceProfileRegistry,
            listener = object : Ft10cScanCoordinator.Listener {
                override fun onDeviceFound(device: BluetoothDevice, address: String, name: String, rssi: Int) {
                    devices[address] = device
                    if (selectedAddress == address) selected = device
                    runOnUiThread { showOrUpdateDevice(address, name, rssi) }
                }

                override fun onLog(message: String) = log(message)

                override fun onScanStateChanged(scanning: Boolean) {
                    runOnUiThread {
                        deviceScanning = scanning
                        publishDeviceComposeState(scanningOverride = scanning)
                    }
                }

            }
        )
    }
    private val scanCoordinator: Ft10cScanCoordinator by scanCoordinatorDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedDarkTheme = getSharedPreferences("jetsport_settings", Context.MODE_PRIVATE).getBoolean("dark_theme", false)
        setTheme(if (savedDarkTheme) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        appCoreViewModel = ViewModelProvider(
            this,
            AppCoreViewModel.Factory(applicationContext, handler)
        )[AppCoreViewModel::class.java]
        appCore = appCoreViewModel.core
        phoneFinderAlertActive = appCore.phoneFinderAlert.isActive
        phoneFinderSoundEnabled = settingsRepository.phoneFinderSoundEnabled
        phoneFinderVibrationEnabled = settingsRepository.phoneFinderVibrationEnabled
        refreshPhoneFinderRingtoneLabel()
        // Start as a regular started+bound service while the Activity is visible.
        // It is promoted to connectedDevice foreground mode immediately before GATT connect.
        val serviceIntent = Intent(this, Ft10cBleForegroundService::class.java)
        try {
            startService(serviceIntent)
            serviceBound = bindService(serviceIntent, bleServiceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            log("Failed to start BLE service: ${e.javaClass.simpleName}: ${e.message}")
        }
        dashboardViewModel = ViewModelProvider(this, DashboardViewModel.Factory(measurementRepository))[DashboardViewModel::class.java]
        historyViewModel = ViewModelProvider(this, HistoryViewModel.Factory(measurementRepository))[HistoryViewModel::class.java]
        val screenStateStore = ScreenStateStore(
            initialTabIndex = savedInstanceState?.getInt(ScreenStateStore.STATE_ACTIVE_TAB, 0) ?: 0,
            initialHistoryExpanded = savedInstanceState?.getBoolean(ScreenStateStore.STATE_HISTORY_EXPANDED, false) ?: false
        )
        screenLifecycle = ScreenLifecycleCoordinator(
            refreshHistory = ::refreshHistory,
            stateStore = screenStateStore,
            refreshToday = ::refreshDashboard
        )
        dashboardController = DashboardScreenController(
            activeDayRepository = activeDayRepository,
            viewModel = dashboardViewModel,
            profileProvider = ::stepProfile,
            languageProvider = { appLanguage },
            logger = ::log,
            initialBatteryPercent = settingsRepository.lastBatteryPercent
        )
        appActionCoordinator = AppActionCoordinator(object : AppActionCoordinator.Handlers {
            override fun showLanguage() = showLanguageDialog()
            override fun showProfile() = showStepProfileDialog()
            override fun scan() = startScan()
            override fun connect() = connectSelected()
            override fun disconnect() = disconnectBracelet("отключено пользователем")
            override fun refreshHistory() {
                this@MainActivity.refreshHistory()
                requestStepHistory("manual")
            }
            override fun syncTime() = syncBraceletTime(automatic = false)
            override fun chooseDateTime() = chooseManualDateTime()
            override fun exportLog() = exportTechnicalLog()
            override fun selectTab(index: Int) = screenLifecycle.onTabSelected(index)
            override fun selectDevice(address: String) = this@MainActivity.selectDevice(address)
            override fun setAutomaticTimeSync(enabled: Boolean) = this@MainActivity.setTimeSyncMode(enabled)
        })
        lifecycleScope.launch {
            appCore.bleUiEvents.events.collect { event ->
                when (event) {
                    BleUiEvent.TechnicalLogChanged -> publishDeviceComposeState()
                    is BleUiEvent.PhoneFinderAlertChanged -> phoneFinderAlertActive = event.active
                    BleUiEvent.PhoneFinderActivated -> Unit // The foreground BLE service owns alert playback.
                    BleUiEvent.BlePermissionsRequired -> requestBlePermissions()
                    is BleUiEvent.TimeSyncStatus -> {
                        updateDeviceUiState { copy(syncStatus = event.message) }
                        publishDeviceComposeState()
                    }
                    is BleUiEvent.CanSyncTimeChanged -> {
                        updateDeviceUiState { copy(canSyncTime = event.enabled) }
                        publishDeviceComposeState()
                    }
                    is BleUiEvent.TimeWriteCompleted -> {
                        updateDeviceUiState {
                            copy(
                                canSyncTime = event.connected,
                                syncStatus = when {
                                    event.timedOut -> "Нет callback BLE-записи за 5 секунд. Изменение часов не подтверждено."
                                    event.status == BluetoothGatt.GATT_SUCCESS -> "BLE-запись времени завершена. Изменение часов браслета отдельно не подтверждено."
                                    else -> "Ошибка BLE-записи времени: status=${event.status}"
                                }
                            )
                        }
                        publishDeviceComposeState()
                    }
                    is BleUiEvent.GattConnected -> {
                        refreshDeviceRows()
                        updateDeviceUiState { copy(connected = true, canConnect = false) }
                        updateConnectionWarning()
                        publishDeviceComposeState()
                    }
                    is BleUiEvent.GattDisconnected -> {
                        dashboardController.onDisconnected()
                        refreshDeviceRows()
                        updateConnectionWarning()
                        updateDeviceUiState { copy(connected = false, canConnect = selected != null,
                            canSyncTime = false, canChooseDateTime = false,
                            syncStatus = "Нет подключения к браслету") }
                        refreshDashboard()
                        publishDeviceComposeState()
                    }
                    is BleUiEvent.UnsupportedGattDevice -> {
                        updateDeviceUiState { copy(connected = false, canConnect = selected != null,
                            canSyncTime = false, canChooseDateTime = false,
                            syncStatus = if (appLanguage == "ru") "Это не JetSport FT-10C. Соединение закрыто; команды не отправлялись."
                            else "This is not a JetSport FT-10C. Connection closed; no commands were sent.") }
                        refreshDeviceRows()
                        updateConnectionWarning()
                        publishDeviceComposeState()
                    }
                    is BleUiEvent.GattProfileReady -> {
                        updateDeviceUiState { copy(canSyncTime = event.timeWriteAvailable,
                            canChooseDateTime = event.timeCharacteristicFound && connectedAddress != null && !timeSyncState.automaticMode,
                            syncStatus = if (event.timeWriteAvailable) {
                                if (timeSyncState.automaticMode) "FT-10C подключён. Передача профиля и синхронизация…" else "FT-10C подключён. Выбран ручной режим времени."
                            } else "Характеристика записи времени не найдена") }
                        publishDeviceComposeState()
                    }
                    is BleUiEvent.GattConnectTimedOut -> {
                        refreshDeviceRows()
                        updateDeviceUiState {
                            copy(connected = false, canConnect = selected != null, canSyncTime = false, canChooseDateTime = false,
                                syncStatus = "Не удалось установить GATT-соединение: нет ответа устройства за 15 секунд")
                        }
                        updateConnectionWarning()
                        publishDeviceComposeState()
                    }
                    is BleUiEvent.GattServiceDiscoveryTimedOut -> {
                        refreshDeviceRows()
                        updateDeviceUiState {
                            copy(connected = false, canConnect = selected != null, canSyncTime = false, canChooseDateTime = false,
                                syncStatus = "Браслет подключён, но не ответил на запрос списка GATT-сервисов за 15 секунд")
                        }
                        updateConnectionWarning()
                        publishDeviceComposeState()
                    }
                    is BleUiEvent.GattServiceDiscoveryFailed -> {
                        refreshDeviceRows()
                        updateDeviceUiState {
                            copy(connected = false, canConnect = selected != null, canSyncTime = false, canChooseDateTime = false,
                                syncStatus = "Не удалось получить GATT-сервисы браслета (status=${event.status})")
                        }
                        updateConnectionWarning()
                        publishDeviceComposeState()
                    }
                    is BleUiEvent.LiveDayChanged -> dashboardController.onLivePacket(event.dayKey)
                    is BleUiEvent.LiveStepsChanged -> {
                        dashboardController.updateStepHistoryStatus("last_hour", event.count)
                        refreshDashboard()
                        refreshHistory()
                    }
                    is BleUiEvent.HistoryProgress -> dashboardController.updateStepHistoryStatus("receiving", event.receivedRecords)
                    is BleUiEvent.HistoryCommitted -> {
                        refreshDashboard()
                        refreshHistory()
                        dashboardController.updateStepHistoryStatus("synced", event.recordCount)
                    }
                    is BleUiEvent.BatteryReceived -> dashboardController.updateBattery(event.percent)
                    is BleUiEvent.HeartRateReceived -> {
                        dashboardController.updateHeartRate(event.bpm, event.time)
                        refreshHistory()
                    }
                    is BleUiEvent.BloodPressureReceived -> {
                        dashboardController.updateBloodPressure(event.systolic, event.diastolic, event.time)
                        refreshHistory()
                    }
                }
            }
        }
        appLanguage = settingsRepository.language
        appDarkTheme = settingsRepository.darkTheme
        appDateFormat = settingsRepository.dateFormat
        refreshPhoneFinderRingtoneLabel()
        buildUi()
        lifecycleScope.launch { dashboardViewModel.state.collect { dashboardController.render(it) } }
        lifecycleScope.launch { historyViewModel.state.collect { state ->
            historyComposeState = state
            state.errorMessage?.let { log("HISTORY DATABASE READ ERROR: $it") }
        } }
        refreshDashboard()
        refreshHistory()
        log("JetSport FT-10C Sync ${BuildConfig.VERSION_NAME} — FT-10C profile, step history, distance and calories")
        log("Detailed notifications are logged only for FT-10C; other devices remain visible without detailed logging.")
        log("Android API=${Build.VERSION.SDK_INT}; manufacturer=${Build.MANUFACTURER}; model=${Build.MODEL}")
        log("Time sync test mode: using the command from JetSport 1.8.0; the actual bracelet clock change is not independently verified.")
    }

    private fun buildUi() {
        // Device tab and diagnostics are rendered exclusively by Compose.
        publishDeviceComposeState()

        updateConnectionWarning()
        val composeRoot = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                JetSportTheme(darkTheme = appDarkTheme) {
                    JetSportComposeHost(
                        language = appLanguage,
                        initialTab = screenLifecycle.activeTabIndex,
                        todayState = todayComposeState,
                        historyState = historyComposeState,
                        historyExpanded = screenLifecycle.historyExpanded,
                        deviceState = deviceComposeState,
                        onHistoryRefresh = { appActionCoordinator.dispatch(AppAction.RefreshHistory) },
                        onHistoryExpandedChanged = screenLifecycle::setHistoryExpanded,
                        onSettingsProfile = { appActionCoordinator.dispatch(AppAction.ShowProfile) },
                        onDeviceScan = { appActionCoordinator.dispatch(AppAction.Scan) },
                        onDeviceConnect = { appActionCoordinator.dispatch(AppAction.Connect) },
                        onDeviceDisconnect = { appActionCoordinator.dispatch(AppAction.Disconnect) },
                        onDeviceSelected = { appActionCoordinator.dispatch(AppAction.SelectDevice(it)) },
                        onSettingsSyncModeChanged = { appActionCoordinator.dispatch(AppAction.SetAutomaticTimeSync(it)) },
                        onSettingsTimeSyncIntervalChanged = ::setTimeSyncInterval,
                        onSettingsChooseDateTime = { appActionCoordinator.dispatch(AppAction.ChooseDateTime) },
                        onSettingsSyncTime = { appActionCoordinator.dispatch(AppAction.SyncTime) },
                        phoneFinderSoundEnabled = phoneFinderSoundEnabled,
                        onPhoneFinderSoundChanged = { enabled -> phoneFinderSoundEnabled = enabled; settingsRepository.phoneFinderSoundEnabled = enabled; log("PHONE FINDER: sound ${if (enabled) "enabled" else "disabled"}") },
                        phoneFinderVibrationEnabled = phoneFinderVibrationEnabled,
                        onPhoneFinderVibrationChanged = { enabled -> phoneFinderVibrationEnabled = enabled; settingsRepository.phoneFinderVibrationEnabled = enabled; log("PHONE FINDER: vibration ${if (enabled) "enabled" else "disabled"}") },
                        phoneFinderAlertActive = phoneFinderAlertActive,
                        onStopPhoneFinder = { appCore.phoneFinderAlert.stop() },
                        phoneFinderRingtoneLabel = phoneFinderRingtoneLabel,
                        onChoosePhoneFinderRingtone = ::launchPhoneFinderRingtonePicker,
                        onSettingsExportLog = { appActionCoordinator.dispatch(AppAction.ExportLog) },
                        onSettingsLogVisibilityChanged = { visible -> updateDeviceUiState { copy(logVisible = visible) }; publishDeviceComposeState() },
                        onSettingsLanguageChanged = { next ->
                            if (next != appLanguage) {
                                settingsRepository.language = next
                                appLanguage = next
                                refreshPhoneFinderRingtoneLabel()
                                log("UI language changed to ${next.uppercase(Locale.ROOT)} without restarting BLE connection")
                            }
                        },
                        darkTheme = appDarkTheme,
                        onDarkThemeChanged = { enabled ->
                            settingsRepository.darkTheme = enabled
                            setTheme(if (enabled) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
                            appDarkTheme = enabled
                            log(if (enabled) "Dark theme enabled" else "Light theme enabled")
                        },
                        dateFormat = appDateFormat,
                        automaticTimeSyncIntervalMinutes = settingsRepository.automaticTimeSyncIntervalMinutes,
                        onDateFormatChanged = { format ->
                            settingsRepository.dateFormat = format
                            appDateFormat = format
                            log("Date format changed to ${if (format == "MDY") "MM.DD.YYYY" else "DD.MM.YYYY"}")
                        },
                        connectionWarning = connectionWarningText,
                        onTabSelected = { appActionCoordinator.dispatch(AppAction.SelectTab(it)) }
                    )
                }
            }
        }
        setContentView(composeRoot)
        if (::dashboardController.isInitialized) {
            dashboardController.bindTodayStateListener { todayComposeState = it }
        }
    }

    @Suppress("DEPRECATION")
    private fun getPickedRingtoneUri(data: Intent?): Uri? = if (Build.VERSION.SDK_INT >= 33) {
        data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }

    private fun refreshPhoneFinderRingtoneLabel() {
        phoneFinderRingtoneLabel = when (val stored = settingsRepository.phoneFinderRingtoneUri) {
            "__SILENT__" -> if (appLanguage == "ru") "Без звука" else "Silent"
            null -> if (appLanguage == "ru") "По умолчанию" else "System default"
            else -> runCatching {
                RingtoneManager.getRingtone(this, Uri.parse(stored))?.getTitle(this)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: if (appLanguage == "ru") "Выбранная мелодия" else "Selected ringtone"
        }
    }

    private fun launchPhoneFinderRingtonePicker() {
        val existing = when (val stored = settingsRepository.phoneFinderRingtoneUri) {
            "__SILENT__" -> null
            null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            else -> Uri.parse(stored)
        }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, if (appLanguage == "ru") "Мелодия поиска телефона" else "Phone finder ringtone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        }
        phoneFinderRingtonePicker.launch(intent)
    }

    private fun showLanguageDialog() {
        val choices = arrayOf("Русский", "English")
        android.app.AlertDialog.Builder(this)
            .setTitle("Language / Язык")
            .setItems(choices) { _, which ->
                val next = if (which == 0) "ru" else "en"
                settingsRepository.language = next
                appLanguage = next
                refreshPhoneFinderRingtoneLabel()
                // The AppCore BLE runtime survives this UI rebuild; only Compose views are recreated.
                val knownDevices = deviceListStateStore.snapshot()
                buildUi()
                refreshDashboard()
                knownDevices.forEach { row -> showOrUpdateDevice(row.address, row.name, row.rssi) }
                updateConnectionWarning()
                updateDeviceUiState { copy(connected = appCore.bleSessionRuntime.hasGatt && connectedAddress != null,
                    canConnect = selected != null && connectedAddress == null,
                    canSyncTime = connectedAddress != null && !timeSyncState.writePending) }
                log("UI language changed to ${next.uppercase(Locale.ROOT)} without restarting BLE connection")
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(ScreenStateStore.STATE_ACTIVE_TAB, screenLifecycle.activeTabIndex)
        outState.putBoolean(ScreenStateStore.STATE_HISTORY_EXPANDED, screenLifecycle.historyExpanded)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun cancelDeferredHistoryRequest() {
        appCore.bleSessionRuntime.cancelHistory()
    }

    private fun requestStepHistory(trigger: String, force: Boolean = false) {
        appCore.bleSessionRuntime.requestHistory(trigger, force)
    }

    private fun stepProfile(): StepProfile {
        val p = userProfileRepository.load()
        return StepProfile(p.heightCm, p.weightKg, p.isMale, p.ageYears)
    }


    private fun sendUserProfileToBracelet(trigger: String) {
        appCore.bleSessionRuntime.sendProfile(trigger)
    }

    private fun showStepProfileDialog() {
        val current = stepProfile()
        val heightInput = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(current.heightCm.toString()) }
        val weightInput = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(current.weightKg.toString()) }
        val ageInput = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(current.ageYears.toString()) }
        val genderGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(RadioButton(this@MainActivity).apply { id = 1; text = if (appLanguage == "ru") "Мужской" else "Male" })
            addView(RadioButton(this@MainActivity).apply { id = 2; text = if (appLanguage == "ru") "Женский" else "Female" })
            check(if (current.isMale) 1 else 2)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(this@MainActivity).apply { text = if (appLanguage == "ru") "Рост, см" else "Height, cm" })
            addView(heightInput)
            addView(TextView(this@MainActivity).apply { text = if (appLanguage == "ru") "Вес, кг" else "Weight, kg" })
            addView(weightInput)
            addView(TextView(this@MainActivity).apply { text = if (appLanguage == "ru") "Возраст, лет" else "Age, years" })
            addView(ageInput)
            addView(TextView(this@MainActivity).apply { text = if (appLanguage == "ru") "Пол" else "Sex" })
            addView(genderGroup)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (appLanguage == "ru") "Параметры расчёта" else "Calculation profile")
            .setView(content)
            .setNegativeButton(if (appLanguage == "ru") "Отмена" else "Cancel", null)
            .setPositiveButton(if (appLanguage == "ru") "Сохранить" else "Save") { _, _ ->
                val h = heightInput.text.toString().toIntOrNull()
                val w = weightInput.text.toString().toIntOrNull()
                val age = ageInput.text.toString().toIntOrNull()
                if (h == null || h !in 100..230 || w == null || w !in 25..250 || age == null || age !in 5..100) {
                    android.widget.Toast.makeText(this, if (appLanguage == "ru") "Проверь рост (100–230 см), вес (25–250 кг) и возраст (5–100 лет)" else "Check height (100–230 cm), weight (25–250 kg) and age (5–100 years)", android.widget.Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val male = genderGroup.checkedRadioButtonId == 1
                userProfileRepository.save(h, w, age, male)
                refreshDashboard()
                log("STEP PROFILE: saved height=${h}cm weight=${w}kg age=${age} sex=${if (male) "male" else "female"}")
                if (connectedAddress != null) sendUserProfileToBracelet("profile changed")
            }.show()
    }

    private fun refreshDashboard() {
        if (::dashboardController.isInitialized) dashboardController.refresh()
    }

    private fun refreshHistory() {
        if (::historyViewModel.isInitialized) historyViewModel.refresh()
    }

    private fun selectDevice(address: String) {
        selected = devices[address]
        selectedAddress = address
        updateDeviceUiState { copy(canConnect = selected != null && connectedAddress == null) }
        log("Selected: ${deviceListStateStore.nameFor(address) ?: "BLE device"} / $address")
        refreshDeviceRows()
        publishDeviceComposeState()
    }

    private fun showOrUpdateDevice(address: String, name: String, rssi: Int) {
        deviceListStateStore.upsert(address, name, rssi, selectedAddress, connectedAddress)
        publishDeviceComposeState()
    }

    private fun refreshDeviceRows() {
        deviceListStateStore.refresh(selectedAddress, connectedAddress)
        publishDeviceComposeState()
    }

    private fun startScan() {
        if (scanCoordinatorDelegate.isInitialized() && scanCoordinator.isScanning) {
            log("Scan is already running; duplicate start request ignored")
            return
        }
        if (!hasBlePermissions()) {
            log("Required BLE permissions are missing; requesting permissions")
            requestBlePermissions()
            return
        }
        val bt = adapter
        if (bt == null) { log("BluetoothAdapter is unavailable"); return }
        if (!bt.isEnabled) { log("Bluetooth is disabled"); return }

        // Scanning refreshes discovery results, not the independent active GATT session.
        // Keep the connected device visible even if it emits no advertising packets.
        val activeAddress = connectedAddress
        // Capture the mutable Activity property once; Kotlin cannot smart-cast it
        // safely from inside the linkedSetOf receiver lambda.
        val currentSelectedAddress = selectedAddress
        val keepAddresses = linkedSetOf<String>().apply {
            if (activeAddress != null) add(activeAddress)
            if (currentSelectedAddress != null &&
                (currentSelectedAddress == activeAddress || devices.containsKey(currentSelectedAddress))
            ) {
                add(currentSelectedAddress)
            }
        }
        val previousRows = deviceListStateStore.snapshot().associateBy { it.address }
        val retainedDevices = keepAddresses.mapNotNull { address ->
            val knownDevice = devices[address] ?: runCatching { bt.getRemoteDevice(address) }.getOrNull()
            knownDevice?.let { address to it }
        }.toMap()
        devices.clear()
        devices.putAll(retainedDevices)
        selected = selectedAddress?.let { devices[it] }
        deviceListStateStore.retainAddresses(keepAddresses, selectedAddress, activeAddress)
        // If a retained device was absent from the previous state snapshot, reconstruct its
        // row from Android's BluetoothDevice object instead of silently dropping it.
        keepAddresses.forEach { address ->
            if (!deviceListStateStore.contains(address)) {
                val name = previousRows[address]?.name
                    ?: runCatching { bt.getRemoteDevice(address).name }.getOrNull()
                    ?: if (address == activeAddress) "FT-10C" else "BLE device"
                showOrUpdateDevice(address, name, previousRows[address]?.rssi ?: -60)
            }
        }
        updateDeviceUiState { copy(canConnect = selected != null && connectedAddress == null) }
        refreshDeviceRows()
        try {
            scanCoordinator.start()
        } catch (e: SecurityException) {
            log("SecurityException during startScan: ${e.message}")
        } catch (e: IllegalStateException) {
            log("IllegalStateException during startScan: ${e.message}")
        } catch (e: Exception) {
            log("startScan failed ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun stopScan(reason: String = "по запросу") {
        if (!scanCoordinatorDelegate.isInitialized()) return
        scanCoordinator.stop(reason)
    }

    private fun connectSelected() {
        if (!hasBlePermissions()) { requestBlePermissions(); return }
        val device = selected ?: return
        stopScan("подключение")
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingNotificationConnectDevice = device
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            return
        }
        if (bleService == null) {
            pendingConnectDevice = device
            log("Waiting for BLE foreground service to become ready before connecting")
            return
        }
        beginGattConnection(device)
    }

    private fun beginGattConnection(device: BluetoothDevice) {
        try {
            // Foreground mode is entered before connectGatt so Android sees the
            // connected-device work as user initiated and continuously visible.
            bleService?.startBleForeground()
                ?: throw IllegalStateException("BLE Foreground Service ещё не подключён")
            appCore.bleSessionRuntime.connect(device, safeAddress(device) ?: "<unknown>")
        } catch (e: SecurityException) {
            bleService?.stopBleForeground()
            log("Missing permission for connection/foreground service: ${e.message}")
        } catch (e: Exception) {
            bleService?.stopBleForeground()
            log("Failed to start GATT connection: ${e.javaClass.simpleName}: ${e.message}")
            runOnUiThread { updateDeviceUiState { copy(syncStatus = "Ошибка запуска GATT-подключения") }; refreshDeviceRows() }
        }
    }

    private fun disconnectBracelet(reason: String) {
        stopScan("отключение браслета")
        appCore.bleSessionRuntime.disconnect(reason)
        bleService?.stopBleForeground()
        updateDeviceUiState { copy(connected = false, canConnect = selected != null, canSyncTime = false, canChooseDateTime = false, syncStatus = "Браслет отключён") }
        updateConnectionWarning()
        refreshDeviceRows()
        publishDeviceComposeState()
        log("GATT closed: $reason")
    }

    private fun updateConnectionWarning() {
        connectionWarningText = if (connectedAddress == null) {
            if (appLanguage == "ru")
                "Браслет не подключён. Откройте вкладку «Устройство», чтобы подключиться."
            else "Bracelet is not connected. Open the Device tab to connect."
        } else null
    }

    private fun hasBlePermissions(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestBlePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        requestPermissions(permissions, 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            permissions.forEachIndexed { i, permission ->
                val granted = grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED
                log("Permission $permission = ${if (granted) "GRANTED" else "DENIED"}")
            }
            if (hasBlePermissions()) log("Permissions granted. Tap \"Find Bracelet\" again.")
        } else if (requestCode == 101) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            log("Permission ${Manifest.permission.POST_NOTIFICATIONS} = ${if (granted) "GRANTED" else "DENIED"}; BLE connection will proceed")
            pendingNotificationConnectDevice?.also { device ->
                pendingNotificationConnectDevice = null
                if (bleService == null) pendingConnectDevice = device else beginGattConnection(device)
            }
        }
    }

    private fun setTimeSyncMode(automatic: Boolean) {
        timeSyncState.automaticMode = automatic
        updateDeviceUiState { copy(automaticSync = automatic) }
        settingsRepository.automaticTimeSync = automatic
        appCore.bleSessionRuntime.onTimeSyncSettingsChanged()
        val connected = connectedAddress != null
        updateDeviceUiState { copy(canChooseDateTime = connected && !automatic,
            canSyncTime = connected && !timeSyncState.writePending,
            syncStatus = if (automatic) "Синхронизация при подключении и далее периодически"
            else "Ручной режим. Выбери дату и время, затем нажми «Синхронизировать время»") }
        publishDeviceComposeState()
        log("TIME SYNC MODE: ${if (automatic) "AUTO" else "MANUAL"}")
    }

    private fun setTimeSyncInterval(intervalMinutes: Int) {
        if (intervalMinutes !in SettingsRepository.TIME_SYNC_INTERVALS_MINUTES) return
        settingsRepository.automaticTimeSyncIntervalMinutes = intervalMinutes
        appCore.bleSessionRuntime.onTimeSyncSettingsChanged()
        publishDeviceComposeState()
        log("AUTO TIME SYNC: interval set to $intervalMinutes min")
    }

    private fun chooseManualDateTime() {
        val now = timeSyncState.manualDateTime
        DatePickerDialog(this, { _, year, month, day ->
            timeSyncState.manualDateTime.set(Calendar.YEAR, year)
            timeSyncState.manualDateTime.set(Calendar.MONTH, month)
            timeSyncState.manualDateTime.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(this, { _, hour, minute ->
                timeSyncState.manualDateTime.set(Calendar.HOUR_OF_DAY, hour)
                timeSyncState.manualDateTime.set(Calendar.MINUTE, minute)
                timeSyncState.manualDateTime.set(Calendar.SECOND, 0)
                timeSyncState.manualDateTime.set(Calendar.MILLISECOND, 0)
                timeSyncState.manualDateTimeChosen = true
                val selectedDateTime = SimpleDateFormat(if (settingsRepository.dateFormat == "MDY") "MM.dd.yyyy HH:mm" else "dd.MM.yyyy HH:mm", Locale.getDefault()).format(timeSyncState.manualDateTime.time)
                updateDeviceUiState { copy(canSyncTime = connectedAddress != null && !timeSyncState.writePending,
                    selectedDateTime = selectedDateTime, syncStatus = "Выбрано: $selectedDateTime") }
                publishDeviceComposeState()
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun requestBatteryA2(force: Boolean = false) {
        appCore.bleSessionRuntime.requestBattery(force)
    }

    private fun syncBraceletTime(automatic: Boolean = false) {
        appCore.bleSessionRuntime.syncTime(automatic)
    }

    private fun safeAddress(device: BluetoothDevice): String? = try { device.address } catch (_: SecurityException) { null }
    private fun safeDeviceName(device: BluetoothDevice): String? = try { device.name } catch (_: SecurityException) { null }

    private fun log(message: String) {
        runOnUiThread {
            // AppCore owns the single journal for both Activity and background BLE events.
            appCore.appendTechnicalLog(message)
            publishDeviceComposeState()
        }
    }

    private fun updateDeviceUiState(transform: com.jetsport.ft10csync.presentation.DeviceUiState.() -> com.jetsport.ft10csync.presentation.DeviceUiState) {
        deviceComposeState = deviceComposeState.transform()
    }

    private fun publishDeviceComposeState(scanningOverride: Boolean? = null) {
        updateDeviceUiState {
            copy(
                devices = deviceListStateStore.snapshot(),
                scanning = scanningOverride ?: deviceScanning,
                canConnect = selected != null && connectedAddress == null,
                connected = connectedAddress != null,
                canSyncTime = canSyncTime && connectedAddress != null && !timeSyncState.writePending,
                canChooseDateTime = canChooseDateTime && connectedAddress != null && !timeSyncState.automaticMode,
                automaticSync = timeSyncState.automaticMode,
                logText = technicalLogStore.snapshot()
            )
        }
    }

    private fun exportTechnicalLog() {
        val snapshot = technicalLogStore.snapshot()
        if (snapshot.isBlank()) {
            log("Log export: no entries yet")
            return
        }
        pendingExportLogContent = snapshot
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "JetSport_FT10C_log_$stamp.txt")
        }
        try {
            startActivityForResult(intent, exportLogRequestCode)
        } catch (e: Exception) {
            log("LOG EXPORT: cannot open document picker: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with the project's Activity-based UI")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != exportLogRequestCode || resultCode != RESULT_OK || data?.data == null) return
        val content = pendingExportLogContent ?: technicalLogStore.snapshot()
        pendingExportLogContent = null
        try {
            contentResolver.openOutputStream(data.data!!, "wt")?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: throw IllegalStateException("Unable to open selected document")
            log("LOG EXPORT: saved ${content.toByteArray(Charsets.UTF_8).size} bytes as TXT")
        } catch (e: Exception) {
            log("LOG EXPORT ERROR: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun validateBleAvailabilityOnResume() {
        if (!hasBlePermissions()) {
            handleBleUnavailable("Необходимые разрешения Bluetooth отозваны или не предоставлены")
            return
        }
        val enabled = try {
            adapter?.isEnabled == true
        } catch (error: SecurityException) {
            false
        }
        if (enabled) {
            onBluetoothAdapterAvailabilityChanged(true)
        } else {
            handleBleUnavailable("Bluetooth is disabled")
        }
    }

    private fun onBluetoothAdapterAvailabilityChanged(enabled: Boolean) {
        if (!enabled) {
            handleBleUnavailable("Bluetooth is disabled")
            return
        }
        if (!hasBlePermissions()) {
            handleBleUnavailable("Необходимые разрешения Bluetooth отозваны или не предоставлены")
            return
        }
        val previousReason = bluetoothUnavailableReason
        bluetoothUnavailableReason = null
        if (previousReason != null) {
            log("Bluetooth enabled; automatic reconnection is not performed")
            updateDeviceUiState {
                copy(
                    connected = false,
                    canConnect = selected != null,
                    canSyncTime = false,
                    canChooseDateTime = false,
                    syncStatus = if (appLanguage == "ru") "Bluetooth включён. Нажмите «Найти браслет» для нового подключения" else "Bluetooth is on. Tap Find bracelet to connect again"
                )
            }
            refreshDeviceRows()
            publishDeviceComposeState()
            updateConnectionWarning()
        }
    }

    private fun handleBleUnavailable(reason: String) {
        if (bluetoothUnavailableReason == reason) return
        bluetoothUnavailableReason = reason
        stopScan(reason)
        runCatching { appCore.bleSessionRuntime.disconnect(reason) }
            .onSuccess { bleService?.stopBleForeground() }
            .onFailure { log("BLE cleanup after availability loss failed: ${it.javaClass.simpleName}: ${it.message}") }
        cancelDeferredHistoryRequest()
        dashboardController.onDisconnected()
        updateDeviceUiState {
            copy(
                connected = false,
                canConnect = false,
                canSyncTime = false,
                canChooseDateTime = false,
                syncStatus = if (appLanguage == "ru") reason else if (reason == "Bluetooth is disabled") "Bluetooth is turned off" else "Bluetooth permissions are required"
            )
        }
        updateConnectionWarning()
        refreshDeviceRows()
        publishDeviceComposeState()
        log("BLE unavailable: $reason; scanning and pending synchronization stopped; manual reconnect required")
    }

    override fun onStart() {
        super.onStart()
        bluetoothStateMonitor.register()
    }

    override fun onStop() {
        // A foreground refresh must never outlive the Activity's foreground visit.
        if (::appCore.isInitialized) syncCoordinator.cancelForegroundRefresh()
        if (bluetoothStateMonitorDelegate.isInitialized()) bluetoothStateMonitor.unregister()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::appCore.isInitialized) phoneFinderAlertActive = appCore.phoneFinderAlert.isActive
        validateBleAvailabilityOnResume()
        // Re-evaluate the calendar day on every foreground return. This is a local
        // database refresh; it does not itself issue an additional BLE command.
        refreshDashboard()
        // Refresh only while the UI is foregrounded; no alarm, wake lock or background polling.
        if (bluetoothUnavailableReason == null) {
            syncCoordinator.scheduleForegroundRefresh(
                isConnected = { appCore.bleSessionRuntime.isConnected },
                onHistory = { requestStepHistory("app foreground", force = false) },
                onBattery = { requestBatteryA2(force = false) }
            )
        } else {
            syncCoordinator.cancelForegroundRefresh()
        }
    }

    override fun onDestroy() {
        if (isFinishing || isChangingConfigurations) {
            stopScan(if (isFinishing) "закрытие приложения" else "пересоздание Activity")
        }
        // A connected foreground service outlives the Activity/task. When no GATT
        // session remains, stop the started service; its onDestroy closes AppCore.
        if (isFinishing && ::appCore.isInitialized && !appCore.bleSessionRuntime.hasGatt) {
            stopService(Intent(this, Ft10cBleForegroundService::class.java))
        }
        if (serviceBound) {
            runCatching { unbindService(bleServiceConnection) }
            serviceBound = false
        }
        super.onDestroy()
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
