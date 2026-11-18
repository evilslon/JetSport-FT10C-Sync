package com.jetsport.ft10csync.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Divider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import com.jetsport.ft10csync.presentation.HistoryUiState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale

private data class TabSpec(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JetSportComposeHost(
    language: String,
    initialTab: Int,
    todayState: TodayScreenState?,
    historyState: HistoryUiState,
    historyExpanded: Boolean,
    deviceState: DeviceUiState,
    onHistoryRefresh: () -> Unit,
    onHistoryExpandedChanged: (Boolean) -> Unit,
    onSettingsProfile: () -> Unit,
    onDeviceScan: () -> Unit,
    onDeviceConnect: () -> Unit,
    onDeviceDisconnect: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onSettingsSyncModeChanged: (Boolean) -> Unit,
    onSettingsChooseDateTime: () -> Unit,
    onSettingsSyncTime: () -> Unit,
    onSettingsExportLog: () -> Unit,
    onSettingsLogVisibilityChanged: (Boolean) -> Unit,
    onSettingsLanguageChanged: (String) -> Unit,
    darkTheme: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
    dateFormat: String,
    onDateFormatChanged: (String) -> Unit,
    connectionWarning: String?,
    onTabSelected: (Int) -> Unit
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(ScreenLifecycleCoordinator.TODAY_TAB_INDEX, ScreenLifecycleCoordinator.LAST_TAB_INDEX)) }
    val tabs = if (language == "ru") listOf(
        TabSpec("Сегодня", Icons.Outlined.Home),
        TabSpec("Здоровье", Icons.Outlined.FavoriteBorder),
        TabSpec("История", Icons.Outlined.History),
        TabSpec("Устройство", Icons.Outlined.Devices),
        TabSpec("Настройки", Icons.Outlined.Settings)
    ) else listOf(
        TabSpec("Today", Icons.Outlined.Home),
        TabSpec("Health", Icons.Outlined.FavoriteBorder),
        TabSpec("History", Icons.Outlined.History),
        TabSpec("Device", Icons.Outlined.Devices),
        TabSpec("Settings", Icons.Outlined.Settings)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("JetSport Sync", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (language == "ru") "Здоровье и активность" else "Health and activity",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            onTabSelected(index)
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, fontSize = 9.sp, lineHeight = 10.sp) },
                        alwaysShowLabel = true
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (connectionWarning != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        connectionWarning,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> TodayComposeScreen(state = todayState, language = language)
                    1 -> HealthComposeScreen(state = todayState, language = language)
                    2 -> HistoryComposeScreen(state = historyState, language = language, dateFormat = dateFormat, expanded = historyExpanded, onRefresh = onHistoryRefresh, onExpandedChanged = onHistoryExpandedChanged)
                    3 -> DeviceComposeScreen(state = deviceState, language = language, onScan = onDeviceScan, onConnect = onDeviceConnect, onDisconnect = onDeviceDisconnect, onSelect = onDeviceSelected)
                    4 -> SettingsComposeScreen(state = deviceState, language = language, onProfile = onSettingsProfile, onSyncModeChanged = onSettingsSyncModeChanged, onChooseDateTime = onSettingsChooseDateTime, onSyncTime = onSettingsSyncTime, onExportLog = onSettingsExportLog, onLogVisibilityChanged = onSettingsLogVisibilityChanged, onLanguageChanged = onSettingsLanguageChanged, darkTheme = darkTheme, onDarkThemeChanged = onDarkThemeChanged, dateFormat = dateFormat, onDateFormatChanged = onDateFormatChanged)
                }
            }
        }
    }
}

@Composable
private fun DeviceComposeScreen(
    state: DeviceUiState,
    language: String,
    onScan: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSelect: (String) -> Unit
) {
    val ru = language == "ru"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (ru) "УСТРОЙСТВО" else "DEVICE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.scanning) { Text(if (state.scanning) { if (ru) "Идёт поиск…" else "Scanning…" } else if (ru) "НАЙТИ БРАСЛЕТ" else "FIND BRACELET") }
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = state.canConnect) { Text(if (ru) "ПОДКЛЮЧИТЬСЯ" else "CONNECT") }
        Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = state.connected) { Text(if (ru) "ОТКЛЮЧИТЬ БРАСЛЕТ" else "DISCONNECT BRACELET") }
        Text(if (ru) "Найденные устройства" else "Discovered devices", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.devices.isEmpty()) Text(if (ru) "Устройства пока не найдены" else "No devices found yet", style = MaterialTheme.typography.bodyMedium)
        state.devices.forEach { device ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(device.address) },
                colors = CardDefaults.outlinedCardColors(containerColor = if (device.connected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = device.selected, onClick = { onSelect(device.address) })
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge)
                        Text("${device.address}  RSSI ${device.rssi} dBm", style = MaterialTheme.typography.bodyMedium)
                        Text(if (device.connected) { if (ru) "● ПОДКЛЮЧЕНО" else "● CONNECTED" } else if (device.selected) { if (ru) "Выбрано для подключения" else "Selected" } else { if (ru) "Не подключено" else "Not connected" }, style = MaterialTheme.typography.labelMedium, color = if (device.connected) Color(0xFF28734A) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(if (state.connected) { if (ru) "Браслет подключён. Параметры синхронизации находятся во вкладке «Настройки»." else "Bracelet connected. Synchronization options are in Settings." } else { if (ru) "Выберите браслет и подключитесь. Параметры приложения находятся во вкладке «Настройки»." else "Select and connect a bracelet. App preferences are in Settings." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsComposeScreen(
    state: DeviceUiState,
    language: String,
    onProfile: () -> Unit,
    onSyncModeChanged: (Boolean) -> Unit,
    onChooseDateTime: () -> Unit,
    onSyncTime: () -> Unit,
    onExportLog: () -> Unit,
    onLogVisibilityChanged: (Boolean) -> Unit,
    onLanguageChanged: (String) -> Unit,
    darkTheme: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
    dateFormat: String,
    onDateFormatChanged: (String) -> Unit
) {
    val ru = language == "ru"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(if (ru) "НАСТРОЙКИ" else "SETTINGS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (ru) "ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ" else "USER PROFILE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        OutlinedButton(onClick = onProfile, modifier = Modifier.fillMaxWidth()) { Text(if (ru) "Рост, вес, возраст и пол" else "Height, weight, age and sex") }
        Divider()
        Text(if (ru) "СИНХРОНИЗАЦИЯ ВРЕМЕНИ" else "TIME SYNCHRONIZATION", style = MaterialTheme.typography.titleMedium)
        val visibleSyncStatus = state.syncStatus.takeUnless { it.contains("Изменение часов браслета отдельно не подтверждено", ignoreCase = true) || it.contains("Bracelet clock change is not separately confirmed", ignoreCase = true) }.orEmpty()
        if (visibleSyncStatus.isNotBlank()) Text(visibleSyncStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().height(36.dp).selectable(state.automaticSync, role = Role.RadioButton) { onSyncModeChanged(true) }) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) { RadioButton(selected = state.automaticSync, onClick = { onSyncModeChanged(true) }, modifier = Modifier.size(34.dp)) }
            Text(if (ru) "Автоматически при подключении" else "Automatically on connection")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().height(36.dp).selectable(!state.automaticSync, role = Role.RadioButton) { onSyncModeChanged(false) }) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) { RadioButton(selected = !state.automaticSync, onClick = { onSyncModeChanged(false) }, modifier = Modifier.size(34.dp)) }
            Text(if (ru) "Вручную" else "Manually")
        }
        OutlinedButton(onClick = onChooseDateTime, enabled = state.canChooseDateTime, modifier = Modifier.fillMaxWidth()) { Text(state.selectedDateTime.ifBlank { if (ru) "ВЫБРАТЬ ДАТУ И ВРЕМЯ" else "CHOOSE DATE AND TIME" }) }
        Button(onClick = onSyncTime, enabled = state.canSyncTime, modifier = Modifier.fillMaxWidth()) { Text(if (ru) "СИНХРОНИЗИРОВАТЬ ВРЕМЯ" else "SYNC TIME") }
        Divider()
        Text(if (ru) "ОФОРМЛЕНИЕ" else "APPEARANCE", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().height(36.dp).selectable(!darkTheme, role = Role.RadioButton) { onDarkThemeChanged(false) }) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) { RadioButton(selected = !darkTheme, onClick = { onDarkThemeChanged(false) }, modifier = Modifier.size(34.dp)) }
            Text(if (ru) "Светлое" else "Light")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().height(36.dp).selectable(darkTheme, role = Role.RadioButton) { onDarkThemeChanged(true) }) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) { RadioButton(selected = darkTheme, onClick = { onDarkThemeChanged(true) }, modifier = Modifier.size(34.dp)) }
            Text(if (ru) "Тёмное" else "Dark")
        }
        Divider()
        Text(if (ru) "ЯЗЫК ИНТЕРФЕЙСА" else "INTERFACE LANGUAGE", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().height(36.dp).selectable(ru, role = Role.RadioButton) { onLanguageChanged("ru") }) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) { RadioButton(selected = ru, onClick = { onLanguageChanged("ru") }, modifier = Modifier.size(34.dp)) }
            Text("Русский")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().height(36.dp).selectable(!ru, role = Role.RadioButton) { onLanguageChanged("en") }) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) { RadioButton(selected = !ru, onClick = { onLanguageChanged("en") }, modifier = Modifier.size(34.dp)) }
            Text("English")
        }
        Divider()
        Text(if (ru) "ФОРМАТ ДАТЫ" else "DATE FORMAT", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().height(36.dp).selectable(dateFormat == "DMY", role = Role.RadioButton) { onDateFormatChanged("DMY") }) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) { RadioButton(selected = dateFormat == "DMY", onClick = { onDateFormatChanged("DMY") }, modifier = Modifier.size(34.dp)) }
            Text("ДД.ММ.ГГГГ")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().height(36.dp).selectable(dateFormat == "MDY", role = Role.RadioButton) { onDateFormatChanged("MDY") }) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) { RadioButton(selected = dateFormat == "MDY", onClick = { onDateFormatChanged("MDY") }, modifier = Modifier.size(34.dp)) }
            Text("ММ.ДД.ГГГГ")
        }
        Divider()
        Text(if (ru) "ДИАГНОСТИКА" else "DIAGNOSTICS", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onLogVisibilityChanged(!state.logVisible) }, modifier = Modifier.fillMaxWidth()) { Text(if (state.logVisible) { if (ru) "СКРЫТЬ ТЕХНИЧЕСКИЙ ЖУРНАЛ" else "HIDE TECHNICAL LOG" } else { if (ru) "ПОКАЗАТЬ ТЕХНИЧЕСКИЙ ЖУРНАЛ" else "SHOW TECHNICAL LOG" }) }
        OutlinedButton(onClick = onExportLog, modifier = Modifier.fillMaxWidth()) { Text(if (ru) "СОХРАНИТЬ ЖУРНАЛ В TXT" else "SAVE LOG AS TXT") }
        if (state.logVisible) {
            val lines = remember(state.logText) { state.logText.lineSequence().filter { it.isNotBlank() }.toList() }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                LazyColumn(Modifier.fillMaxWidth().height(260.dp).padding(8.dp)) {
                    items(lines) { line -> Text(line, style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                }
            }
        }
    }
}

@Composable
private fun TodayComposeScreen(state: TodayScreenState?, language: String) {
    val ru = language == "ru"
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (ru) "СЕГОДНЯ" else "TODAY", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MetricCard(
            title = if (ru) "ШАГИ СЕГОДНЯ" else "TODAY'S STEPS",
            value = if (state?.hasTodayData == true) state.steps.toString() else "—",
            unit = if (ru) "шагов" else "steps",
            accent = Color(0xFF28734A),
            detail = state?.stepHistoryStatus ?: if (state?.hasTodayData == true) {
                if (ru) "Данные за сегодня" else "Today's data"
            } else if (ru) "Ожидание данных за сегодня" else "Waiting for today's data"
        )
        MetricCard(
            title = if (ru) "БАТАРЕЯ БРАСЛЕТА" else "BRACELET BATTERY",
            value = state?.batteryPercent?.toString() ?: "—",
            unit = "%",
            accent = if ((state?.batteryPercent ?: 100) <= 20) Color(0xFFB23A48) else Color(0xFF28734A),
            detail = if (ru) "Последний достоверный ответ браслета" else "Last valid bracelet response"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = if (ru) "РАССТОЯНИЕ" else "DISTANCE",
                value = if (state?.hasTodayData == true) String.format(Locale.US, "%.2f", state.activity.distanceKm) else "—",
                unit = if (ru) "км" else "km",
                accent = Color(0xFF28734A),
                detail = if (ru) "По данным профиля" else "Profile-based estimate"
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = if (ru) "КАЛОРИИ" else "CALORIES",
                value = if (state?.hasTodayData == true) state.activity.caloriesKcal.toInt().toString() else "—",
                unit = if (ru) "ккал" else "kcal",
                accent = Color(0xFFB36B20),
                detail = if (ru) "Оценка активности" else "Activity estimate"
            )
        }
        Text(
            if (ru) "Новые измерения автоматически сохраняются в телефоне." else "New measurements are saved automatically on the phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun HistoryComposeScreen(
    state: HistoryUiState,
    language: String,
    dateFormat: String,
    expanded: Boolean,
    onRefresh: () -> Unit,
    onExpandedChanged: (Boolean) -> Unit
) {
    val ru = language == "ru"
    val snapshot = state.snapshot
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (ru) "ИСТОРИЯ ИЗМЕРЕНИЙ" else "MEASUREMENT HISTORY", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (snapshot == null) { if (ru) "История ещё загружается" else "History is loading" }
            else if (ru) "Сохранено измерений: ${snapshot.measurementCount} · Пульс: ${snapshot.heartRate.size} · Давление: ${snapshot.bloodPressure.size}"
            else "Saved measurements: ${snapshot.measurementCount} · Heart rate: ${snapshot.heartRate.size} · Blood pressure: ${snapshot.bloodPressure.size}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.isLoading && snapshot == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        state.errorMessage?.let { Text(if (ru) "Ошибка чтения истории: $it" else "History read error: $it", color = MaterialTheme.colorScheme.error) }
        // Repository already returns measurements in chronological order. Keep that
        // order so the x-axis represents time from oldest to newest.
        val hr = snapshot?.heartRate.orEmpty()
        HistoryChartCard(
            title = if (ru) "Пульс · последние 30 измерений" else "Heart rate · last 30 readings",
            values = listOf(hr.map { it.value1 }),
            timestamps = hr.map { it.timestamp },
            labels = listOf(if (ru) "Пульс" else "Heart rate"),
            colors = listOf(Color(0xFFB23A48)),
            unit = if (ru) "уд/мин" else "bpm",
            emptyText = if (ru) "Нет сохранённых измерений пульса" else "No saved heart-rate readings",
            dateFormat = dateFormat
        )
        val bp = snapshot?.bloodPressure.orEmpty()
        HistoryChartCard(
            title = if (ru) "Давление · верхнее и нижнее" else "Blood pressure · systolic and diastolic",
            values = listOf(bp.map { it.value1 }, bp.map { it.value2 }),
            timestamps = bp.map { it.timestamp },
            labels = if (ru) listOf("Верхнее", "Нижнее") else listOf("Systolic", "Diastolic"),
            colors = listOf(Color(0xFF245A86), Color(0xFF4D9B83)),
            unit = if (ru) "мм рт. ст." else "mmHg",
            emptyText = if (ru) "Нет сохранённых измерений давления" else "No saved blood-pressure readings",
            dateFormat = dateFormat
        )
        val steps = sortDailyStepsChronologically(snapshot?.dailySteps.orEmpty())
        HistoryStepsCard(steps, ru, dateFormat)
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(if (ru) "Запросить историю с браслета" else "Request history from bracelet") }
    }
}

@Composable
private fun HistoryChartCard(
    title: String,
    values: List<List<Float>>,
    timestamps: List<Long>,
    labels: List<String>,
    colors: List<Color>,
    unit: String,
    emptyText: String,
    dateFormat: String
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (values.all { it.isEmpty() }) {
                Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val all = values.flatten()
                val rawMin = all.minOrNull() ?: 0f
                val rawMax = all.maxOrNull() ?: 1f
                val span = (rawMax - rawMin).coerceAtLeast(1f)
                val minValue = kotlin.math.floor((rawMin - span * 0.12f) / 10f) * 10f
                val maxValue = kotlin.math.ceil((rawMax + span * 0.12f) / 10f) * 10f
                val safeMax = if (maxValue <= minValue) minValue + 10f else maxValue
                val tickValues = (0..4).map { safeMax - (safeMax - minValue) * it / 4f }
                val chartTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                val chartGridColor = MaterialTheme.colorScheme.outlineVariant
                var selectedPoint by remember(timestamps, values) { mutableIntStateOf(-1) }
                if (selectedPoint in timestamps.indices) {
                    val date = java.text.SimpleDateFormat(if (dateFormat == "MDY") "MM.dd.yyyy HH:mm:ss" else "dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                        .format(java.util.Date(timestamps[selectedPoint]))
                    val reading = values.mapIndexedNotNull { index, series ->
                        series.getOrNull(selectedPoint)?.let { "${labels.getOrElse(index) { "Value" }}: ${it.toInt()}" }
                    }.joinToString(" · ")
                    Text("$date  ·  $reading $unit", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(if (Locale.getDefault().language == "ru") "Нажмите на точку для просмотра измерения" else "Tap a point to see its reading",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Canvas(Modifier.fillMaxWidth().height(208.dp).pointerInput(timestamps, values) {
                    detectTapGestures { tap ->
                        if (timestamps.isNotEmpty()) {
                            val left = 52.dp.toPx()
                            val right = size.width.toFloat() - 32.dp.toPx()
                            val fraction = ((tap.x - left) / (right - left).coerceAtLeast(1f)).coerceIn(0f, 1f)
                            selectedPoint = (fraction * (timestamps.size - 1)).toInt().coerceIn(0, timestamps.lastIndex)
                        }
                    }
                }) {
                    val left = 52.dp.toPx()
                    val right = size.width - 32.dp.toPx()
                    val top = 14f
                    val bottom = size.height - 42f
                    val native = drawContext.canvas.nativeCanvas
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = chartTextColor
                        textSize = 10f * density
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    tickValues.forEachIndexed { index, tick ->
                        val y = top + (bottom - top) * index / 4f
                        drawLine(chartGridColor, Offset(left, y), Offset(right, y), 1f)
                        native.drawText(tick.toInt().toString(), left - 7f, y + 4f, paint)
                    }
                    val axisPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = chartTextColor
                        textSize = 9f * density
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    if (timestamps.isNotEmpty()) {
                        val formatter = java.text.SimpleDateFormat(if (dateFormat == "MDY") "MM.dd" else "dd.MM", Locale.getDefault())
                        native.drawText(formatter.format(java.util.Date(timestamps.first())), left, size.height - 8f, axisPaint)
                        if (timestamps.size > 1) native.drawText(
                            formatter.format(java.util.Date(timestamps.last())), right, size.height - 8f, axisPaint)
                    }
                    values.forEachIndexed { series, data ->
                        if (data.isNotEmpty()) {
                            val denom = (data.size - 1).coerceAtLeast(1)
                            val points = data.mapIndexed { i, value ->
                                Offset(left + (right - left) * i / denom, bottom - (value - minValue) / (safeMax - minValue) * (bottom - top))
                            }
                            points.zipWithNext().forEach { (a, b) -> drawLine(colors.getOrElse(series) { Color.Gray }, a, b, 2.5f, cap = StrokeCap.Round) }
                            points.forEachIndexed { index, point ->
                                drawCircle(colors.getOrElse(series) { Color.Gray }, if (index == selectedPoint) 10.dp.toPx() else 4.dp.toPx(), point)
                                if (index == selectedPoint) drawCircle(Color.White, 3.dp.toPx(), point)
                            }
                        }
                    }
                }
                Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    labels.forEachIndexed { i, label ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.width(9.dp).height(9.dp).background(colors.getOrElse(i) { Color.Gray }, RoundedCornerShape(5.dp)))
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStepsCard(steps: List<Pair<String, Int>>, ru: Boolean, dateFormat: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (ru) "Шаги · история по дням" else "Steps · daily history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (steps.isEmpty()) Text(if (ru) "Нет сохранённой истории шагов" else "No saved step history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val maxSteps = (steps.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                steps.takeLast(14).forEach { (day, count) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val displayDate = runCatching {
                            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(day)
                            if (parsed != null) java.text.SimpleDateFormat(if (dateFormat == "MDY") "MM.dd.yyyy" else "dd.MM.yyyy", java.util.Locale.getDefault()).format(parsed) else day
                        }.getOrElse { day }
                        Text(displayDate, modifier = Modifier.width(88.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Box(Modifier.weight(1f).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))) {
                            Box(Modifier.fillMaxWidth(count.toFloat() / maxSteps).height(12.dp).background(Color(0xFF28734A), RoundedCornerShape(6.dp)))
                        }
                        Text(count.toString(), modifier = Modifier.width(58.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthComposeScreen(state: TodayScreenState?, language: String) {
    val ru = language == "ru"
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (ru) "ПОСЛЕДНИЕ ИЗМЕРЕНИЯ" else "LATEST MEASUREMENTS", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MetricCard(
            title = if (ru) "ПУЛЬС" else "HEART RATE",
            value = state?.heartRateBpm?.toString() ?: "—",
            unit = if (ru) "уд/мин" else "bpm",
            accent = Color(0xFFB23A48),
            detail = state?.heartRateTime?.let { if (ru) "Последнее измерение: $it" else "Last measurement: $it" }
                ?: if (ru) "Ожидание измерения с браслета" else "Waiting for bracelet measurement"
        )
        MetricCard(
            title = if (ru) "АРТЕРИАЛЬНОЕ ДАВЛЕНИЕ" else "BLOOD PRESSURE",
            value = if (state?.systolic != null && state.diastolic != null) "${state.systolic} / ${state.diastolic}" else "— / —",
            unit = if (ru) "мм рт. ст." else "mmHg",
            accent = Color(0xFF245A86),
            detail = state?.pressureTime?.let { if (ru) "Последнее измерение: $it" else "Last measurement: $it" }
                ?: if (ru) "Ожидание измерения с браслета" else "Waiting for bracelet measurement"
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                if (ru) "Показаны последние значения, полученные от браслета. Новые измерения автоматически обновляют экран и сохраняются в телефоне. Показатели предназначены для наблюдения и не являются медицинским диагнозом."
                else "The latest values received from the bracelet are shown here. New measurements update this screen automatically and are saved on the phone. These readings are for tracking and are not a medical diagnosis.",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    accent: Color,
    detail: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
                Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
