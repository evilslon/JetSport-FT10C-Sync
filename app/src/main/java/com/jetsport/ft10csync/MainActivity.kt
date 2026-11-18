package com.jetsport.ft10csync

import android.Manifest
import androidx.activity.ComponentActivity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.jetsport.ft10csync.ble.Ft10cScanCoordinator
import com.jetsport.ft10csync.ble.BluetoothStateMonitor
import com.jetsport.ft10csync.ble.BleUiEvent
import com.jetsport.ft10csync.ble.BleUiEventDispatcher
import com.jetsport.ft10csync.ble.Ft10cNotificationCoordinator
import com.jetsport.ft10csync.ble.BleConnectionStateStore
import com.jetsport.ft10csync.ble.Ft10cDeviceManager
import com.jetsport.ft10csync.ble.Ft10cConnectionCoordinator
import com.jetsport.ft10csync.ble.Ft10cGattWriteEventHandler
import com.jetsport.ft10csync.ble.Ft10cGattSessionEventHandler
import com.jetsport.ft10csync.device.BraceletDeviceProfile
import com.jetsport.ft10csync.device.Ft10cDeviceProfile
import com.jetsport.ft10csync.ble.Ft10cCommandQueue
import com.jetsport.ft10csync.ble.HandlerFt10cQueueScheduler
import com.jetsport.ft10csync.presentation.AppAction
import com.jetsport.ft10csync.presentation.AppActionCoordinator
import com.jetsport.ft10csync.presentation.DashboardViewModel
import com.jetsport.ft10csync.presentation.DashboardScreenController
import com.jetsport.ft10csync.presentation.HistoryViewModel
import com.jetsport.ft10csync.presentation.ScreenLifecycleCoordinator
import com.jetsport.ft10csync.presentation.ScreenStateStore
import com.jetsport.ft10csync.sync.Ft10cHistoryCoordinator
import com.jetsport.ft10csync.sync.Ft10cTimeSyncState
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
    private val stepHistoryTimeout = Runnable {
        historyCoordinator.onTimeout()?.let { timeout ->
            log("STEP HISTORY: timeout waiting for B2 FD; discarded incomplete session (${timeout.discardedRecordCount} staged records); B2 RX blocked until reconnect because protocol has no request ID; previous saved history retained")
        }
    }
    private lateinit var screenLifecycle: ScreenLifecycleCoordinator
    private var appLanguage by mutableStateOf("ru")
    private var appDarkTheme by mutableStateOf(false)
    private var appDateFormat by mutableStateOf("DMY")
    private lateinit var dashboardViewModel: DashboardViewModel
    private lateinit var dashboardController: DashboardScreenController
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var appActionCoordinator: AppActionCoordinator
    private lateinit var bleUiEventDispatcher: BleUiEventDispatcher
    private lateinit var notificationCoordinator: Ft10cNotificationCoordinator
    private var deviceScanning = false
    private var pendingWriteKind: String? = null
    // Active bracelet day is domain state, not derived from the phone calendar.
    private var selected: BluetoothDevice? = null
    private var selectedAddress: String? = null
    // Connection lifecycle and GATT session coordination live outside Activity.
    private val bleConnectionState: BleConnectionStateStore get() = deviceManager.connectionState
    private val connectedAddress: String? get() = bleConnectionState.connectedAddress
    // The device manager coordinates the session; Ft10cGattClient owns the platform GATT object.
    private val handler = Handler(Looper.getMainLooper())
    private var deferredHistoryRequest: Runnable? = null
    private lateinit var appCore: AppCore
    private val measurementRepository get() = appCore.measurementRepository
    private val settingsRepository get() = appCore.settingsRepository
    private val userProfileRepository get() = appCore.userProfileRepository
    private val activeDayRepository get() = appCore.activeDayRepository
    private val historyCoordinator get() = appCore.historyCoordinator
    private val timeSyncState get() = appCore.timeSyncState
    private val syncCoordinator get() = appCore.syncCoordinator
    private val batteryCoordinator get() = appCore.batteryCoordinator
    private val technicalLogStore get() = appCore.technicalLogStore
    private val connectionCoordinator: Ft10cConnectionCoordinator by lazy {
        Ft10cConnectionCoordinator(
            handler = handler,
            deviceManager = deviceManager,
            isConnected = { address -> connectedAddress == address },
            logger = ::log,
            listener = object : Ft10cConnectionCoordinator.Listener {
                override fun onBeforeConnect() {
                    if (commandQueueDelegate.isInitialized()) commandQueue.cancelAll("replacing GATT connection")
                    // Cancel delayed sync and foreground refresh work tied to the previous session.
                    syncCoordinator.onDisconnected()
                    batteryCoordinator.onDisconnected()
                    pendingWriteKind = null
                    timeSyncState.markWriteCompleted()
                    cancelDeferredHistoryRequest()
                    historyCoordinator.cancelActiveSession()
                    handler.removeCallbacks(stepHistoryTimeout)
                    activeDeviceProfile = null
                    dashboardController.onDisconnected()
                }

                override fun onConnectTimeout(address: String) {
                    runOnUiThread {
                        refreshDeviceRows()
                        updateDeviceUiState {
                            copy(connected = false, canConnect = selected != null, canSyncTime = false, canChooseDateTime = false,
                                syncStatus = "Не удалось установить GATT-соединение: нет ответа устройства за 15 секунд")
                        }
                        updateConnectionWarning()
                    }
                }

                override fun onServiceDiscoveryTimeout(address: String) {
                    runOnUiThread {
                        refreshDeviceRows()
                        updateDeviceUiState {
                            copy(connected = false, canConnect = selected != null, canSyncTime = false, canChooseDateTime = false,
                                syncStatus = "Браслет подключён, но не ответил на запрос списка GATT-сервисов за 15 секунд")
                        }
                        updateConnectionWarning()
                        publishDeviceComposeState()
                    }
                }

                override fun onServiceDiscoveryFailed(address: String, status: Int) {
                    runOnUiThread {
                        refreshDeviceRows()
                        updateDeviceUiState {
                            copy(connected = false, canConnect = selected != null, canSyncTime = false, canChooseDateTime = false,
                                syncStatus = "Не удалось получить GATT-сервисы браслета (status=$status)")
                        }
                        updateConnectionWarning()
                        publishDeviceComposeState()
                    }
                }

                override fun onBeforeDisconnect(reason: String) {
                    activeDeviceProfile = null
                    if (commandQueueDelegate.isInitialized()) commandQueue.cancelAll("manual disconnect: $reason")
                    syncCoordinator.onDisconnected()
                    batteryCoordinator.onDisconnected()
                    timeSyncState.onDisconnected()
                    pendingWriteKind = null
                }
            }
        )
    }

    private val adapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private var bluetoothUnavailableReason: String? = null
    private val bluetoothStateMonitorDelegate = lazy { BluetoothStateMonitor(this, ::onBluetoothAdapterAvailabilityChanged, ::log) }
    private val bluetoothStateMonitor: BluetoothStateMonitor by bluetoothStateMonitorDelegate

    // The registry currently contains FT-10C only. Other models can be added
    // without changing the common scanner or enabling unverified commands.
    private val deviceProfileRegistry get() = appCore.deviceProfileRegistry
    private var activeDeviceProfile: BraceletDeviceProfile? = null

    private val scanCoordinatorDelegate = lazy {
        Ft10cScanCoordinator(
            adapter = adapter ?: error("BluetoothAdapter отсутствует"),
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

    private val deviceManager: Ft10cDeviceManager by lazy {
        Ft10cDeviceManager(this, object : Ft10cDeviceManager.Listener {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                gattSessionEventHandler.onConnectionStateChange(g, status, newState)
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                gattSessionEventHandler.onServicesDiscovered(g, status)
                connectionCoordinator.onServicesDiscovered(status)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                gattSessionEventHandler.onNotification(g, characteristic, value)
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                gattWriteEventHandler.onCharacteristicWrite(g, characteristic, status)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                gattSessionEventHandler.onDescriptorWrite(g, descriptor, status)
                gattWriteEventHandler.onDescriptorWrite(g, descriptor, status)
            }
        }).also { it.setLogSink(::log) }
    }
    private val gattSessionEventHandler: Ft10cGattSessionEventHandler by lazy {
        Ft10cGattSessionEventHandler(
            profileRegistry = deviceProfileRegistry,
            timeServiceUuid = FT10C_SERVICE_UUID,
            timeWriteCharacteristicUuid = FT10C_TIME_WRITE_UUID,
            addressOf = { gatt -> safeAddress(gatt.device) ?: "<unknown>" },
            statusName = ::gattStatusName,
            stateName = ::gattStateName,
            properties = ::properties,
            logger = ::log,
            markUnknown = { address -> deviceManager.markUnknown(address) },
            markReady = { address, modelId -> deviceManager.markReady(address, modelId) },
            enableNotifications = { gatt, profile -> deviceManager.enableNotifications(gatt, profile) },
            onConnected = { g ->
                connectionCoordinator.onConnected()
                if (commandQueueDelegate.isInitialized()) commandQueue.resetForNewConnection()
                historyCoordinator.onConnected()
                timeSyncState.resetAutomaticSyncAttempt()
                runOnUiThread { refreshDeviceRows(); updateDeviceUiState { copy(connected = true, canConnect = false) }; updateConnectionWarning(); publishDeviceComposeState() }
                log("GATT device=${safeAddress(g.device)}: подключено. Запрашиваю список сервисов…")
            },
            onDisconnected = { g ->
                connectionCoordinator.onDisconnected()
                activeDeviceProfile = null
                dashboardController.onDisconnected()
                if (commandQueueDelegate.isInitialized()) commandQueue.cancelAll("GATT disconnected")
                syncCoordinator.onDisconnected()
                batteryCoordinator.onDisconnected()
                timeSyncState.onDisconnected()
                pendingWriteKind = null
                cancelDeferredHistoryRequest()
                handler.removeCallbacks(stepHistoryTimeout)
                historyCoordinator.onDisconnected()
                log("GATT device=${safeAddress(g.device)}: соединение закрыто")
                runOnUiThread {
                    refreshDeviceRows()
                    updateConnectionWarning()
                    updateDeviceUiState { copy(connected = false, canConnect = selected != null, canSyncTime = false, canChooseDateTime = false, syncStatus = "Нет подключения к браслету") }
                    refreshDashboard()
                    publishDeviceComposeState()
                }
            },
            onUnknownProfile = { g ->
                activeDeviceProfile = null
                val address = safeAddress(g.device) ?: "<unknown>"
                log("GATT: устройство $address не является поддерживаемым JetSport FT-10C; закрываю соединение")
                // Unknown BLE devices may be inspected in the scan list, but the app
                // must not keep a GATT link open after profile validation fails.
                deviceManager.disconnectAndClose()
                runOnUiThread {
                    refreshDeviceRows()
                    updateDeviceUiState { copy(connected = false, canConnect = selected != null, canSyncTime = false, canChooseDateTime = false,
                        syncStatus = if (appLanguage == "ru") "Это не JetSport FT-10C. Соединение закрыто; команды не отправлялись."
                        else "This is not a JetSport FT-10C. Connection closed; no commands were sent.") }
                    updateConnectionWarning()
                    publishDeviceComposeState()
                }
            },
            onMatchedProfile = { g, profile, timeCharacteristicFound, timeWriteAvailable ->
                activeDeviceProfile = profile
                runOnUiThread {
                    updateDeviceUiState { copy(canSyncTime = timeWriteAvailable,
                        canChooseDateTime = timeCharacteristicFound && connectedAddress != null && !timeSyncState.automaticMode,
                        syncStatus = if (timeWriteAvailable) {
                            if (timeSyncState.automaticMode) "FT-10C подключён. Передача профиля и синхронизация…" else "FT-10C подключён. Выбран ручной режим времени."
                        } else "Характеристика записи времени не найдена") }
                    publishDeviceComposeState()
                    // The coordinator owns timing only; operation implementations stay unchanged.
                    syncCoordinator.scheduleConnectionWorkflow(
                        isCurrentConnection = { connectedAddress != null && gatt === g },
                        automaticTimeSyncEnabled = { timeSyncState.automaticMode && timeWriteAvailable },
                        automaticTimeSyncAlreadyAttempted = { timeSyncState.isAutomaticSyncAttempted() },
                        onProfile = { sendUserProfileToBracelet("connect") },
                        onAutomaticTimeSync = {
                            log("AUTO TIME SYNC: запуск после передачи профиля")
                            syncBraceletTime(automatic = true)
                        },
                        onHistory = { requestStepHistory("connect", force = true) },
                        onBattery = { requestBatteryA2(force = true) }
                    )
                }
            },
            onNotification = { uuid, value -> handleNotification(uuid, value) }
        )
    }

    private val gattWriteEventHandler: Ft10cGattWriteEventHandler by lazy {
        Ft10cGattWriteEventHandler(
            timeWriteCharacteristicUuid = FT10C_TIME_WRITE_UUID,
            isCommandQueueInitialized = { commandQueueDelegate.isInitialized() },
            onWriteCallback = { status -> commandQueue.onWriteCallback(status) },
            logger = ::log,
            addressOf = { gatt -> safeAddress(gatt.device) },
            isCurrentGatt = { callbackGatt -> callbackGatt === gatt }
        )
    }

    private val gatt: BluetoothGatt? get() = deviceManager.gatt
    private val commandQueueDelegate = lazy {
        Ft10cCommandQueue(
            scheduler = HandlerFt10cQueueScheduler(handler),
            writer = ::writeFt10cPacket,
            logger = ::log
        )
    }
    private val commandQueue: Ft10cCommandQueue by commandQueueDelegate

    private fun writeFt10cPacket(packet: ByteArray): Int {
        val currentGatt = gatt ?: return BluetoothGatt.GATT_FAILURE
        val characteristic = currentGatt.getService(FT10C_SERVICE_UUID)
            ?.getCharacteristic(FT10C_TIME_WRITE_UUID)
            ?: return BluetoothGatt.GATT_FAILURE
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
            return BluetoothGatt.GATT_FAILURE
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            currentGatt.writeCharacteristic(
                characteristic,
                packet.copyOf(),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            characteristic.setValue(packet.copyOf())
            @Suppress("DEPRECATION")
            if (currentGatt.writeCharacteristic(characteristic)) BluetoothGatt.GATT_SUCCESS
            else BluetoothGatt.GATT_FAILURE
        }
    }

    private fun onQueuedCommandCompleted(name: String, status: Int, timedOut: Boolean) {
        if (pendingWriteKind == name) pendingWriteKind = null
        when (name) {
            "PROFILE" -> {
                log("PROFILE A9: ${if (timedOut) "тайм-аут ожидания BLE callback" else "BLE write callback status=$status"}; bracelet-side application is confirmed separately by A9 00")
            }
            "TIME" -> {
                timeSyncState.markWriteCompleted()
                runOnUiThread {
                    updateDeviceUiState { copy(canSyncTime = connectedAddress != null,
                        syncStatus = when {
                            timedOut -> "Нет callback BLE-записи за 5 секунд. Изменение часов не подтверждено."
                            status == BluetoothGatt.GATT_SUCCESS -> "BLE-запись времени завершена. Изменение часов браслета отдельно не подтверждено."
                            else -> "Ошибка BLE-записи времени: status=$status"
                        }) }
                    publishDeviceComposeState()
                }
            }
            "BATTERY" -> log(if (timedOut) "BATTERY A2: тайм-аут BLE callback; уведомление A2 всё ещё может прийти" else "BATTERY A2 write callback: status=$status; ждём уведомление A2")
            "HISTORY" -> log(if (timedOut) "STEP HISTORY: тайм-аут BLE write callback; ожидаем/проверяем ответ B2" else "STEP HISTORY: BLE write callback status=$status")
        }
    }

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        notificationCoordinator.handle(uuid, value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedDarkTheme = getSharedPreferences("jetsport_settings", Context.MODE_PRIVATE).getBoolean("dark_theme", false)
        setTheme(if (savedDarkTheme) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        appCore = ViewModelProvider(
            this,
            AppCoreViewModel.Factory(applicationContext, handler)
        )[AppCoreViewModel::class.java].core
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
            logger = ::log
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
        bleUiEventDispatcher = BleUiEventDispatcher(mainExecutor) { event ->
            when (event) {
                is BleUiEvent.LiveStepsChanged -> {
                    dashboardController.updateStepHistoryStatus("last_hour", event.count)
                    refreshDashboard()
                    refreshHistory()
                }
                is BleUiEvent.HistoryProgress -> dashboardController.updateStepHistoryStatus("receiving", event.receivedRecords)
                is BleUiEvent.BatteryReceived -> dashboardController.updateBattery(event.percent)
                is BleUiEvent.HeartRateReceived -> {
                    dashboardController.updateHeartRate(event.bpm, event.time)
                    saveMeasurement("HR", event.bpm.toFloat())
                }
                is BleUiEvent.BloodPressureReceived -> {
                    dashboardController.updateBloodPressure(event.systolic, event.diastolic, event.time)
                    saveMeasurement("BP", event.systolic.toFloat(), event.diastolic.toFloat())
                }
            }
        }
        notificationCoordinator = Ft10cNotificationCoordinator(
            historyCoordinator = historyCoordinator,
            batteryCoordinator = batteryCoordinator,
            measurementRepository = measurementRepository,
            eventDispatcher = bleUiEventDispatcher,
            mainHandler = handler,
            historyTimeout = stepHistoryTimeout,
            onLiveDay = { dayKey -> dashboardController.onLivePacket(dayKey) },
            onHistoryCommitted = { count ->
                refreshDashboard()
                refreshHistory()
                dashboardController.updateStepHistoryStatus("synced", count)
            },
            onLog = ::log
        )
        appLanguage = settingsRepository.language
        appDarkTheme = settingsRepository.darkTheme
        appDateFormat = settingsRepository.dateFormat
        buildUi()
        lifecycleScope.launch { dashboardViewModel.state.collect { dashboardController.render(it) } }
        lifecycleScope.launch { historyViewModel.state.collect { state ->
            historyComposeState = state
            state.errorMessage?.let { log("HISTORY DATABASE READ ERROR: $it") }
        } }
        refreshDashboard()
        refreshHistory()
        log("JetSport FT-10C Sync ${BuildConfig.VERSION_NAME} — профиль FT-10C, история шагов, дистанция и калории")
        log("Подробная реклама журналируется только для FT-10C; остальные устройства остаются в списке без подробных записей.")
        log("Android API=${Build.VERSION.SDK_INT}; manufacturer=${Build.MANUFACTURER}; model=${Build.MODEL}")
        log("Режим тестирования синхронизации времени: используется команда из JetSport 1.8.0; результат часов браслета отдельно не подтверждается.")
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
                        onSettingsChooseDateTime = { appActionCoordinator.dispatch(AppAction.ChooseDateTime) },
                        onSettingsSyncTime = { appActionCoordinator.dispatch(AppAction.SyncTime) },
                        onSettingsExportLog = { appActionCoordinator.dispatch(AppAction.ExportLog) },
                        onSettingsLogVisibilityChanged = { visible -> updateDeviceUiState { copy(logVisible = visible) }; publishDeviceComposeState() },
                        onSettingsLanguageChanged = { next ->
                            if (next != appLanguage) {
                                settingsRepository.language = next
                                appLanguage = next
                                log("Язык интерфейса изменён на ${next.uppercase(Locale.ROOT)} без перезапуска BLE-соединения")
                            }
                        },
                        darkTheme = appDarkTheme,
                        onDarkThemeChanged = { enabled ->
                            settingsRepository.darkTheme = enabled
                            setTheme(if (enabled) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
                            appDarkTheme = enabled
                            log(if (enabled) "Включена тёмная тема" else "Включена светлая тема")
                        },
                        dateFormat = appDateFormat,
                        onDateFormatChanged = { format ->
                            settingsRepository.dateFormat = format
                            appDateFormat = format
                            log("Формат даты изменён на ${if (format == "MDY") "MM.DD.YYYY" else "DD.MM.YYYY"}")
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

    private fun showLanguageDialog() {
        val choices = arrayOf("Русский", "English")
        android.app.AlertDialog.Builder(this)
            .setTitle("Language / Язык")
            .setItems(choices) { _, which ->
                val next = if (which == 0) "ru" else "en"
                settingsRepository.language = next
                appLanguage = next
                // Keep this Activity and its BluetoothGattCallback alive; only rebuild views.
                val knownDevices = deviceListStateStore.snapshot()
                buildUi()
                refreshDashboard()
                knownDevices.forEach { row -> showOrUpdateDevice(row.address, row.name, row.rssi) }
                updateConnectionWarning()
                updateDeviceUiState { copy(connected = gatt != null && connectedAddress != null,
                    canConnect = selected != null && connectedAddress == null,
                    canSyncTime = connectedAddress != null && !timeSyncState.writePending) }
                log("Язык интерфейса изменён на ${next.uppercase(Locale.ROOT)} без перезапуска BLE-соединения")
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(ScreenStateStore.STATE_ACTIVE_TAB, screenLifecycle.activeTabIndex)
        outState.putBoolean(ScreenStateStore.STATE_HISTORY_EXPANDED, screenLifecycle.historyExpanded)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun saveMeasurement(type: String, first: Float, second: Float = 0f) {
        measurementRepository.insertMeasurement(type, first, second, System.currentTimeMillis()) {
            refreshHistory()
        }
    }

    private fun cancelDeferredHistoryRequest() {
        deferredHistoryRequest?.let(handler::removeCallbacks)
        deferredHistoryRequest = null
        historyCoordinator.resetDeferredRetryBudget()
    }

    private fun requestStepHistory(trigger: String, force: Boolean = false, isDeferredRetry: Boolean = false) {
        val currentGatt = gatt
        if (currentGatt == null || connectedAddress == null || !hasBlePermissions()) {
            log("STEP HISTORY: no active connection; trigger=$trigger")
            return
        }
        if (!isDeferredRetry && deferredHistoryRequest == null) {
            historyCoordinator.resetDeferredRetryBudget()
        }
        val now = System.currentTimeMillis()
        when (historyCoordinator.requestDecision(now, force)) {
            Ft10cHistoryCoordinator.RequestDecision.BLOCKED_UNTIL_RECONNECT -> { log("STEP HISTORY: blocked after previous timeout; reconnect FT-10C before retry; trigger=$trigger"); return }
            Ft10cHistoryCoordinator.RequestDecision.ALREADY_IN_FLIGHT -> { log("STEP HISTORY: already in flight; skip trigger=$trigger"); return }
            Ft10cHistoryCoordinator.RequestDecision.TOO_SOON -> { log("STEP HISTORY: recently requested; skip trigger=$trigger"); return }
            Ft10cHistoryCoordinator.RequestDecision.READY -> Unit
        }
        if (commandQueue.isBusy || pendingWriteKind != null) {
            if (deferredHistoryRequest != null) return
            if (!historyCoordinator.tryScheduleDeferredRetry()) {
                log("STEP HISTORY: deferred retry budget exhausted; waiting for the next trigger")
                return
            }
            log("STEP HISTORY: BLE command pending; defer trigger=$trigger")
            lateinit var retry: Runnable
            retry = Runnable {
                if (deferredHistoryRequest === retry) deferredHistoryRequest = null
                if (connectedAddress != null && gatt === currentGatt) {
                    requestStepHistory(trigger, force, isDeferredRetry = true)
                }
            }
            deferredHistoryRequest = retry
            handler.postDelayed(retry, Ft10cHistoryCoordinator.DEFER_RETRY_MS)
            return
        }
        val characteristic = currentGatt.getService(FT10C_SERVICE_UUID)?.getCharacteristic(FT10C_TIME_WRITE_UUID)
        if (characteristic == null || characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
            log("STEP HISTORY: 0x33F1 unavailable"); return
        }
        val packet = byteArrayOf(0xB2.toByte(), 0xFA.toByte())
        commandQueue.enqueue(
            name = "HISTORY",
            packet = packet,
            onStarted = { pendingWriteKind = "HISTORY" },
            onAccepted = {
                cancelDeferredHistoryRequest()
                historyCoordinator.onRequestAccepted(now)
                handler.removeCallbacks(stepHistoryTimeout)
                handler.postDelayed(stepHistoryTimeout, Ft10cHistoryCoordinator.DEFAULT_TIMEOUT_MS)
                log("STEP HISTORY TX B2 FA accepted; trigger=$trigger")
            },
            onCompleted = { status, timedOut -> onQueuedCommandCompleted("HISTORY", status, timedOut) }
        )
    }

    private fun stepProfile(): StepProfile {
        val p = userProfileRepository.load()
        return StepProfile(p.heightCm, p.weightKg, p.isMale, p.ageYears)
    }


    private fun sendUserProfileToBracelet(trigger: String) {
        val currentGatt = gatt ?: return
        if (connectedAddress == null || !hasBlePermissions()) return
        if (activeDeviceProfile?.modelId != Ft10cDeviceProfile.modelId) {
            log("PROFILE A9: blocked; active device has no verified FT-10C profile")
            return
        }
        if (commandQueue.isBusy || pendingWriteKind != null || timeSyncState.writePending || historyCoordinator.isInFlight) {
            log("PROFILE A9: GATT busy; retry in 2s (trigger=$trigger)")
            handler.postDelayed({ if (connectedAddress != null && gatt === currentGatt) sendUserProfileToBracelet(trigger) }, 2_000)
            return
        }
        val characteristic = currentGatt.getService(FT10C_SERVICE_UUID)?.getCharacteristic(FT10C_TIME_WRITE_UUID)
        if (characteristic == null || characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
            log("PROFILE A9: 0x33F1 WRITE unavailable")
            return
        }
        val profile = stepProfile()
        val packet = try { Ft10cProfileEncoder.encode(profile) } catch (e: Exception) {
            log("PROFILE A9: invalid profile: ${e.message}")
            return
        }
        commandQueue.enqueue(
            name = "PROFILE",
            packet = packet,
            onStarted = { pendingWriteKind = "PROFILE" },
            onAccepted = {
                log("PROFILE A9 TX accepted by Android; trigger=$trigger; height=${profile.heightCm} weight=${profile.weightKg} age=${profile.ageYears} sex=${if (profile.isMale) "male" else "female"}; HEX=${packet.toHex()}")
            },
            onCompleted = { status, timedOut -> onQueuedCommandCompleted("PROFILE", status, timedOut) }
        )
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
        log("Выбрано: ${deviceListStateStore.nameFor(address) ?: "BLE device"} / $address")
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
            log("Сканирование уже выполняется — повторный запуск пропущен")
            return
        }
        if (!hasBlePermissions()) {
            log("Нет необходимых BLE-разрешений; запрашиваю разрешения")
            requestBlePermissions()
            return
        }
        val bt = adapter
        if (bt == null) { log("BluetoothAdapter отсутствует"); return }
        if (!bt.isEnabled) { log("Bluetooth выключен"); return }

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
            log("SecurityException при startScan: ${e.message}")
        } catch (e: IllegalStateException) {
            log("IllegalStateException при startScan: ${e.message}")
        } catch (e: Exception) {
            log("Ошибка startScan ${e.javaClass.simpleName}: ${e.message}")
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
        try {
            connectionCoordinator.connect(device, safeAddress(device) ?: "<unknown>")
        } catch (e: SecurityException) {
            log("Нет разрешения на подключение: ${e.message}")
        } catch (e: Exception) {
            log("Не удалось начать GATT-подключение: ${e.javaClass.simpleName}: ${e.message}")
            runOnUiThread { updateDeviceUiState { copy(syncStatus = "Ошибка запуска GATT-подключения") }; refreshDeviceRows() }
        }
    }

    private fun disconnectBracelet(reason: String) {
        stopScan("отключение браслета")
        connectionCoordinator.disconnect(reason)
        updateDeviceUiState { copy(connected = false, canConnect = selected != null, canSyncTime = false, canChooseDateTime = false, syncStatus = "Браслет отключён") }
        updateConnectionWarning()
        refreshDeviceRows()
        publishDeviceComposeState()
        log("GATT закрыт: $reason")
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
            if (hasBlePermissions()) log("Разрешения получены. Нажми «Найти браслет» ещё раз.")
        }
    }

    private fun setTimeSyncMode(automatic: Boolean) {
        timeSyncState.automaticMode = automatic
        updateDeviceUiState { copy(automaticSync = automatic) }
        settingsRepository.automaticTimeSync = automatic
        val connected = connectedAddress != null
        updateDeviceUiState { copy(canChooseDateTime = connected && !automatic,
            canSyncTime = connected && !timeSyncState.writePending,
            syncStatus = if (automatic) "Автоматическая синхронизация включится при следующем подключении"
            else "Ручной режим. Выбери дату и время, затем нажми «Синхронизировать время»") }
        publishDeviceComposeState()
        log("TIME SYNC MODE: ${if (automatic) "AUTO" else "MANUAL"}")
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
        val currentGatt = gatt ?: return
        batteryCoordinator.request(
            force = force,
            isConnected = { connectedAddress != null && gatt === currentGatt && hasBlePermissions() },
            isBusy = {
                commandQueue.isBusy || timeSyncState.writePending ||
                    historyCoordinator.isInFlight || pendingWriteKind != null
            },
            onFreshSkip = { log("BATTERY A2: skip; refreshed less than 30 minutes ago") },
            onDeferred = { /* Retry is scheduled by the battery coordinator. */ },
            onRetryExhausted = { log("BATTERY A2: retry budget exhausted; waiting for the next scheduled or manual refresh") },
            onRequest = {
                val characteristic = currentGatt.getService(FT10C_SERVICE_UUID)
                    ?.getCharacteristic(FT10C_TIME_WRITE_UUID) ?: return@request
                if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) return@request
                commandQueue.enqueue(
                    name = "BATTERY",
                    packet = byteArrayOf(0xA2.toByte()),
                    onStarted = { pendingWriteKind = "BATTERY" },
                    onAccepted = {
                        log("BATTERY A2 query: write accepted; refresh interval will start only after a valid A2 response")
                    },
                    onCompleted = { status, timedOut -> onQueuedCommandCompleted("BATTERY", status, timedOut) }
                )
            }
        )
    }

    private fun syncBraceletTime(automatic: Boolean = false) {
        
        if (automatic && timeSyncState.isAutomaticSyncAttempted()) {
            log("AUTO TIME SYNC: попытка для этого подключения уже выполнялась")
            return
        }
        val currentGatt = gatt
        if (currentGatt == null || connectedAddress == null) {
            log("TIME SYNC отменена: нет активного BLE-подключения")
            return
        }
        if (!hasBlePermissions()) {
            log("TIME SYNC отменена: отсутствует разрешение Bluetooth Connect")
            requestBlePermissions()
            return
        }
        if (timeSyncState.writePending) {
            log("TIME SYNC: предыдущая запись ещё не завершилась")
            return
        }
        if (commandQueue.isBusy || pendingWriteKind != null || historyCoordinator.isInFlight) {
            log("TIME SYNC: GATT занят; повтор через 2 секунды")
            handler.postDelayed({ if (connectedAddress != null && gatt === currentGatt) syncBraceletTime(automatic) }, 2_000)
            return
        }
        if (activeDeviceProfile?.modelId != Ft10cDeviceProfile.modelId) {
            log("TIME SYNC заблокирована: активный профиль FT-10C не подтверждён")
            updateDeviceUiState { copy(syncStatus = "Синхронизация недоступна для неподдерживаемой модели") }
            return
        }
        val characteristic = currentGatt.getService(FT10C_SERVICE_UUID)
            ?.getCharacteristic(FT10C_TIME_WRITE_UUID)
        if (characteristic == null) {
            log("TIME SYNC: характеристика 0x33F1 в сервисе 0x55FF не найдена")
            updateDeviceUiState { copy(syncStatus = "Не найдена характеристика 0x33F1") }
            return
        }
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
            log("TIME SYNC: 0x33F1 не объявляет WRITE")
            updateDeviceUiState { copy(syncStatus = "0x33F1 не поддерживает WRITE") }
            return
        }

        val calendar = timeSyncState.calendarForSync(automatic)
        val packet = Ft10cTimeSyncState.encodeOriginalTimePacket(calendar)
        val localStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(calendar.time)
        log("TIME SYNC: ${if (automatic) "время телефона" else "ручная дата/время"}=$localStamp")
        log("TIME SYNC: оригинальный пакет JetSport (${packet.size} байт)=${packet.toHex()}")

        updateDeviceUiState { copy(canSyncTime = false) }
        updateDeviceUiState { copy(syncStatus = "Отправка команды времени…") }
        commandQueue.enqueue(
            name = "TIME",
            packet = packet,
            onStarted = { pendingWriteKind = "TIME" },
            onAccepted = {
                timeSyncState.markWriteAccepted()
                updateDeviceUiState { copy(syncStatus = "Запрос записи принят Android. Ожидание BLE callback…") }
                publishDeviceComposeState()
                log("TIME SYNC: writeCharacteristic принят Android; ожидаем onCharacteristicWrite (тайм-аут 5 секунд)")
                if (automatic) log("AUTO TIME SYNC: запрос автоматически отправлен при подключении")
            },
            onCompleted = { status, timedOut -> onQueuedCommandCompleted("TIME", status, timedOut) }
        )
    }

    private fun safeAddress(device: BluetoothDevice): String? = try { device.address } catch (_: SecurityException) { null }
    private fun safeDeviceName(device: BluetoothDevice): String? = try { device.name } catch (_: SecurityException) { null }

    private fun gattStateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN"
    }

    private fun gattStatusName(status: Int): String = when (status) {
        BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
        8 -> "GATT_CONN_TIMEOUT"
        19 -> "GATT_CONN_TERMINATE_PEER_USER"
        22 -> "GATT_CONN_TERMINATE_LOCAL_HOST"
        133 -> "GATT_ERROR (133)"
        else -> "status=$status"
    }

    private fun properties(value: Int): String = buildList {
        if (value and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
        if (value and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
        if (value and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
        if (value and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
        if (value and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
    }.joinToString("|")

    companion object {
        private val FT10C_SERVICE_UUID: UUID = Ft10cDeviceProfile.serviceUuid
        private val FT10C_TIME_WRITE_UUID: UUID = Ft10cDeviceProfile.writeCharacteristicUuid
    }

    private fun log(message: String) {
        runOnUiThread {
            val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "[$stamp] $message\n"
            technicalLogStore.append(line)
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
            log(if (appLanguage == "ru") "Экспорт журнала: пока нет записей" else "Log export: no entries yet")
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
            handleBleUnavailable("Bluetooth выключен")
        }
    }

    private fun onBluetoothAdapterAvailabilityChanged(enabled: Boolean) {
        if (!enabled) {
            handleBleUnavailable("Bluetooth выключен")
            return
        }
        if (!hasBlePermissions()) {
            handleBleUnavailable("Необходимые разрешения Bluetooth отозваны или не предоставлены")
            return
        }
        val previousReason = bluetoothUnavailableReason
        bluetoothUnavailableReason = null
        if (previousReason != null) {
            log("Bluetooth включён; автоматическое переподключение не выполняется")
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
        runCatching { connectionCoordinator.disconnect(reason) }
            .onFailure { log("BLE cleanup after availability loss failed: ${it.javaClass.simpleName}: ${it.message}") }
        cancelDeferredHistoryRequest()
        handler.removeCallbacks(stepHistoryTimeout)
        historyCoordinator.onDisconnected()
        syncCoordinator.onDisconnected()
        batteryCoordinator.onDisconnected()
        timeSyncState.onDisconnected()
        pendingWriteKind = null
        activeDeviceProfile = null
        dashboardController.onDisconnected()
        updateDeviceUiState {
            copy(
                connected = false,
                canConnect = false,
                canSyncTime = false,
                canChooseDateTime = false,
                syncStatus = if (appLanguage == "ru") reason else if (reason == "Bluetooth выключен") "Bluetooth is turned off" else "Bluetooth permissions are required"
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
        validateBleAvailabilityOnResume()
        // Re-evaluate the calendar day on every foreground return. This is a local
        // database refresh; it does not itself issue an additional BLE command.
        refreshDashboard()
        // Refresh only while the UI is foregrounded; no alarm, wake lock or background polling.
        if (bluetoothUnavailableReason == null) {
            syncCoordinator.scheduleForegroundRefresh(
                isConnected = { connectedAddress != null && gatt != null },
                onHistory = { requestStepHistory("app foreground", force = false) },
                onBattery = { requestBatteryA2(force = false) }
            )
        } else {
            syncCoordinator.cancelForegroundRefresh()
        }
    }

    override fun onDestroy() {
        // GATT callbacks are Activity-bound in this generation. If Android recreates
        // the Activity for an unhandled configuration change, close that session too
        // rather than leaving callbacks attached to the destroyed Activity. AppCore
        // itself is retained by AppCoreViewModel across configuration changes.
        if (isFinishing || isChangingConfigurations) {
            stopScan(if (isFinishing) "закрытие приложения" else "пересоздание Activity")
            cancelDeferredHistoryRequest()
            handler.removeCallbacks(stepHistoryTimeout)
            connectionCoordinator.cancelPendingWorkflow()
            if (commandQueueDelegate.isInitialized()) {
                commandQueue.cancelAll(if (isFinishing) "application finishing" else "Activity recreation")
            }
            syncCoordinator.onDisconnected()
            batteryCoordinator.onDisconnected()
            historyCoordinator.onDisconnected()
            timeSyncState.onDisconnected()
            deviceManager.disconnectAndClose()
        }
        // AppCoreViewModel.onCleared() closes application-scoped resources exactly
        // once when the Activity is permanently removed, not during configuration changes.
        super.onDestroy()
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
