package io.github.xingyingyuzhui.modbusmobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import io.github.xingyingyuzhui.modbusmobile.ModbusMobileApp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.Parity
import io.github.xingyingyuzhui.modbusmobile.io.SerialIoWorker
import io.github.xingyingyuzhui.modbusmobile.io.UsbRawSerialPort
import io.github.xingyingyuzhui.modbusmobile.usb.UsbSerialTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transport: UsbSerialTransport? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startAsForeground()
        } catch (error: SecurityException) {
            failStart(error)
            return START_NOT_STICKY
        } catch (error: IllegalStateException) {
            failStart(error)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                app.container.scope.launch { app.container.sessionManager.disconnect() }
            }
            ACTION_OPEN -> scope.launch { open(intent) }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        transport?.close()
        transport = null
        scope.cancel()
        super.onDestroy()
    }

    private fun open(intent: Intent) {
        val bridge = app.container.bridge
        try {
            val opened = openPort(intent)
            transport = opened
            bridge.succeed(opened)
        } catch (error: Exception) {
            transport?.close()
            transport = null
            bridge.fail(error)
            stopSelf()
        }
    }

    private fun openPort(intent: Intent): UsbSerialTransport {
        val repository = app.container.repository
        val deviceId = intent.getIntExtra(EXTRA_DEVICE_ID, -1)
        val portIndex = intent.getIntExtra(EXTRA_PORT_INDEX, 0)
        val device = repository.find(deviceId) ?: error("USB 设备已不在列表中")
        val manager = getSystemService(USB_SERVICE) as android.hardware.usb.UsbManager
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: error("没有匹配的 USB 串口驱动")
        val port = driver.ports.getOrNull(portIndex) ?: error("端口 $portIndex 不存在")
        val connection = manager.openDevice(device) ?: error("打开 USB 设备失败")
        try {
            port.open(connection)
            port.setParameters(
                intent.getIntExtra(EXTRA_BAUD, 9600),
                intent.getIntExtra(EXTRA_DATA_BITS, 8),
                stopBits(intent.getIntExtra(EXTRA_STOP_BITS, 1)),
                parity(intent.getStringExtra(EXTRA_PARITY)),
            )
        } catch (error: Exception) {
            runCatching { port.close() }
            runCatching { connection.close() }
            throw error
        }
        lateinit var serial: UsbSerialTransport
        val worker = SerialIoWorker(
            port = UsbRawSerialPort(port, connection),
            clock = app.container.clock,
            onChunk = { chunk -> serial.emit(chunk) },
            onError = { reason ->
                serial.fail()
                app.container.bridge.onIoError?.invoke(reason)
            },
        )
        serial = UsbSerialTransport(worker)
        worker.start()
        return serial
    }

    private fun failStart(error: Exception) {
        app.container.bridge.fail(error)
        stopSelf()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.session_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = getString(R.string.session_channel_description) },
            )
        }
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.session_notification_title))
            .setContentText(getString(R.string.session_notification_text))
            .setOngoing(true)
            .addAction(0, getString(R.string.session_stop), stop)
            .build()
    }

    private val app: ModbusMobileApp
        get() = application as ModbusMobileApp

    companion object {
        const val ACTION_OPEN = "io.github.xingyingyuzhui.modbusmobile.OPEN"
        const val ACTION_STOP = "io.github.xingyingyuzhui.modbusmobile.STOP"
        const val EXTRA_DEVICE_ID = "deviceId"
        const val EXTRA_PORT_INDEX = "portIndex"
        const val EXTRA_BAUD = "baud"
        const val EXTRA_DATA_BITS = "dataBits"
        const val EXTRA_STOP_BITS = "stopBits"
        const val EXTRA_PARITY = "parity"
        private const val CHANNEL_ID = "modbus_session"
        private const val NOTIFICATION_ID = 7

        private fun stopBits(value: Int): Int = if (value == 2) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1

        private fun parity(name: String?): Int = when (name) {
            Parity.ODD.name -> UsbSerialPort.PARITY_ODD
            Parity.EVEN.name -> UsbSerialPort.PARITY_EVEN
            else -> UsbSerialPort.PARITY_NONE
        }
    }
}
