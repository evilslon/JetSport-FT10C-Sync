package com.jetsport.ft10csync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.jetsport.ft10csync.ble.BleUiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Owns the application BLE runtime after the UI hands AppCore to this service.
 * The service is started while MainActivity is visible and promoted to a
 * connected-device foreground service before the first GATT connection attempt.
 */
class Ft10cBleForegroundService : Service() {
    inner class LocalBinder : Binder() {
        fun service(): Ft10cBleForegroundService = this@Ft10cBleForegroundService
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopWhenUnbound = Runnable {
        if (!isBound && appCore?.bleSessionRuntime?.hasGatt != true) stopSelf()
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appCore: AppCore? = null
    private var isBound = false
    private var isForeground = false
    private var phoneFinderAlertActive = false

    override fun onBind(intent: Intent?): IBinder {
        mainHandler.removeCallbacks(stopWhenUnbound)
        isBound = true
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isBound = false
        return false
    }

    /** Transfers lifecycle responsibility for AppCore and its GATT runtime to this service. */
    internal fun attachCore(core: AppCore) {
        val current = appCore
        if (current === core) return
        check(current == null) { "A different AppCore is already attached to BLE service" }
        appCore = core
        serviceScope.launch {
            core.bleUiEvents.events.collect { event ->
                when (event) {
                    BleUiEvent.PhoneFinderActivated -> core.phoneFinderAlert.start()
                    is BleUiEvent.PhoneFinderAlertChanged -> {
                        phoneFinderAlertActive = event.active
                        showConnectedNotification()
                    }
                    is BleUiEvent.GattConnected -> showConnectedNotification()
                    is BleUiEvent.GattDisconnected,
                    is BleUiEvent.UnsupportedGattDevice,
                    is BleUiEvent.GattConnectTimedOut,
                    is BleUiEvent.GattServiceDiscoveryTimedOut,
                    is BleUiEvent.GattServiceDiscoveryFailed -> {
                        if (event is BleUiEvent.GattDisconnected) core.phoneFinderAlert.stop("BLE connection lost")
                        stopBleForeground()
                        if (!isBound) {
                            // A short grace period prevents a configuration-change
                            // unbind/rebind gap from retiring the shared AppCore.
                            mainHandler.removeCallbacks(stopWhenUnbound)
                            mainHandler.postDelayed(stopWhenUnbound, UNBOUND_STOP_GRACE_MS)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Must be called from the visible Activity immediately before connectGatt is requested. */
    internal fun startBleForeground() {
        createNotificationChannel()
        val notification = buildNotification("Подключение к JetSport FT-10C…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
    }

    private fun showConnectedNotification() {
        if (!isForeground) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(if (phoneFinderAlertActive) "Поиск телефона: сигнал активен" else "JetSport FT-10C подключён"))
    }

    internal fun stopBleForeground() {
        if (!isForeground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        isForeground = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Подключение браслета",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Показывает состояние активного BLE-подключения к JetSport FT-10C"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        if (phoneFinderAlertActive) {
            val stopAlert = PendingIntent.getService(
                this,
                1,
                Intent(this, Ft10cBleForegroundService::class.java).setAction(ACTION_STOP_PHONE_FINDER),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Остановить сигнал", stopAlert)
        }
        return builder
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("JetSport FT-10C Sync")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_PHONE_FINDER) {
            appCore?.phoneFinderAlert?.stop("stopped from notification")
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(stopWhenUnbound)
        serviceScope.cancel()
        appCore?.let { AppCoreRegistry.release(it) }
        appCore = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "jetsport_ft10c_connection"
        private const val NOTIFICATION_ID = 61010
        private const val ACTION_STOP_PHONE_FINDER = "com.jetsport.ft10csync.action.STOP_PHONE_FINDER"
        private const val UNBOUND_STOP_GRACE_MS = 5_000L
    }
}
