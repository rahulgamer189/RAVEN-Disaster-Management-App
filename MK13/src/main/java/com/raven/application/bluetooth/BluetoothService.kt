package com.raven.application.bluetooth

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import com.raven.application.R
import com.raven.application.data.MeshProtocol
import com.raven.application.data.RavenPreferences

import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BluetoothService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val binder = BluetoothBinder()

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var telemetryProvider: TelemetryProvider? = null
    private var senderName: String = ""

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private val serviceUuid = MeshProtocol.serviceUuid


    private val connectionHandlers = ConcurrentHashMap<String, ConnectionHandler>()
    private val meshRouter = MeshRouter()

    private val _messages = MutableSharedFlow<BluetoothMessage>(extraBufferCapacity = 100)
    val messages: SharedFlow<BluetoothMessage> = _messages.asSharedFlow()

    private val _connectedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val connectedDevices: StateFlow<List<BluetoothDeviceDomain>> = _connectedDevices.asStateFlow()

    private var serverJob: Job? = null

    inner class BluetoothBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        senderName = RavenPreferences.readDisplayName(this)
        telemetryProvider = TelemetryProvider(this)

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        scanner = bluetoothAdapter.bluetoothLeScanner

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(getString(R.string.msg_raven_active)),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification(getString(R.string.msg_raven_active)))
        }
        
        startServer()
        // Discovery is now started explicitly from ViewModel after permissions are verified
    }

    fun startDiscovery() {
        Log.d("BluetoothService", "Starting high power mesh discovery")
        // Ensure adapter is on and we have an advertiser/scanner
        if (bluetoothAdapter.state != BluetoothAdapter.STATE_ON) {
            Log.w("BluetoothService", "Bluetooth is off, cannot start discovery")
            return
        }
        
        if (advertiser == null) advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (scanner == null) scanner = bluetoothAdapter.bluetoothLeScanner
        
        startHighPowerDiscovery()
    }

    private fun startHighPowerDiscovery() {
        if (bluetoothAdapter.state != BluetoothAdapter.STATE_ON) {
            Log.w("BluetoothService", "Bluetooth is OFF, cannot start discovery")
            return
        }

        // Reduced power discovery to avoid SecurityException (BLUETOOTH_PRIVILEGED) on some devices
        runCatching {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Save space for UUID
                .addServiceUuid(ParcelUuid(serviceUuid))
                .build()

            advertiser?.stopAdvertising(advertiseCallback)
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        }.onFailure { Log.e("BluetoothService", "Failed to start advertising", it) }

        runCatching {
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner?.stopScan(scanCallback)
            scanner?.startScan(listOf(filter), scanSettings, scanCallback)
        }.onFailure { Log.e("BluetoothService", "Failed to start scan", it) }
    }

        @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            if (!connectionHandlers.containsKey(address)) {
                Log.d("BluetoothService", "New node discovered via BLE: $address (RSSI: ${result.rssi})")
                connectToDevice(address)
            }
        }
    }

    private fun startServer() {
        serverJob?.cancel()
        serverJob = serviceScope.launch {
            val server = BluetoothServer(bluetoothAdapter)
            server.listen { socket ->
                handleConnection(socket)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleConnection(socket: android.bluetooth.BluetoothSocket) {
        val address = socket.remoteDevice.address
        if (connectionHandlers.containsKey(address)) {
            Log.d("BluetoothService", "Already connected to $address, closing new socket")
            runCatching { socket.close() }
            return
        }

        val handler = ConnectionHandler(socket)
        connectionHandlers[address] = handler
        
        updateConnectedDevices()

        serviceScope.launch {
            handler.listenForMessages()
                .onEach { message ->
                    processReceivedMessage(message, address)
                }
                .onCompletion {
                    connectionHandlers.remove(address)
                    updateConnectedDevices()
                    handler.close()
                }
                .collect()
        }
    }

    private suspend fun processReceivedMessage(message: BluetoothMessage, senderAddress: String) {
        if (meshRouter.accept(message)) {
            _messages.emit(message)

            // Relay if possible (TTL > 0)
            val relayed = message.relay()
            if (relayed != null) {
                relayMessage(relayed, senderAddress)
            }

            if (message.messageType == MeshProtocol.SOS || message.messageType == MeshProtocol.BROADCAST) {
                val title = if (message.messageType == MeshProtocol.SOS) getString(R.string.label_sos_emergency) else getString(R.string.label_incoming_broadcast)
                showNotification(title, "${message.senderName}: ${message.message}", message.messageType == MeshProtocol.SOS, message.senderName)
            }
        }
    }

    private suspend fun relayMessage(message: BluetoothMessage, excludeAddress: String) {
        connectionHandlers.filter { it.key != excludeAddress }.forEach { (_, handler) ->
            handler.sendMessage(message)
        }
    }

    private fun updateConnectedDevices() {
        _connectedDevices.value = connectionHandlers.values.map { handler ->
            BluetoothDeviceDomain(
                name = handler.remoteDeviceName ?: "Unknown",
                address = handler.remoteDeviceAddress,
                isPaired = false // We don't necessarily know if it's paired here
            )
        }
    }

        @SuppressLint("MissingPermission")
    fun stopScanning() {
        scanner?.stopScan(scanCallback)
    }

    fun updateSenderName(value: String) {
        senderName = value.trim().take(48)
    }

    fun connectToDevice(address: String) {

        serviceScope.launch {
            val client = BluetoothClient(bluetoothAdapter)
            val socket = client.connect(address)
            socket?.let {
                handleConnection(it)
            }
        }
    }

    fun sendMessage(text: String, type: String = MeshProtocol.CHAT) {
        serviceScope.launch {
                        val telemetry = if (type == MeshProtocol.SOS || type == MeshProtocol.LOCATION) telemetryProvider?.getTelemetry(highAccuracy = true) else null
            val message = BluetoothMessage(
                senderName = senderName.ifBlank { bluetoothAdapter.name ?: getString(R.string.label_sender_me) },

                message = text,
                messageType = type,
                latitude = telemetry?.latitude,
                longitude = telemetry?.longitude,
                batteryPercentage = telemetry?.batteryPercentage
            )
            
            meshRouter.accept(message)
            _messages.emit(message)
            
            connectionHandlers.forEach { (_, handler) ->
                handler.sendMessage(message)
            }
        }
    }

    fun shareLocation() {
                sendMessage(getString(R.string.msg_location_share), type = MeshProtocol.LOCATION)

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val meshChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.label_mesh_service),
                NotificationManager.IMPORTANCE_LOW
            )
            
            val sosChannel = NotificationChannel(
                SOS_CHANNEL_ID,
                getString(R.string.label_sos_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical SOS alerts from the mesh network"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(meshChannel)
            manager.createNotificationChannel(sosChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

            private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BluetoothService", "BLE advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w("BluetoothService", "BLE advertising stopped with code $errorCode")
        }
    }

    private fun showNotification(title: String, content: String, isSos: Boolean = false, sender: String? = null) {
        val channelId = if (isSos) SOS_CHANNEL_ID else CHANNEL_ID
        
        val intent = Intent(this, com.raven.application.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (isSos && sender != null) {
                putExtra("EXTRA_ACTION", "NAVIGATE_TO_SOS")
                putExtra("EXTRA_SENDER", sender)
            }
        }
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 
            if (isSos) SOS_NOTIFICATION_ID else System.currentTimeMillis().toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(if (isSos) android.R.drawable.ic_dialog_alert else android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(if (isSos) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .apply {
                if (isSos) {
                    setCategory(NotificationCompat.CATEGORY_ALARM)
                    setDefaults(Notification.DEFAULT_ALL)
                }
            }
            .build()
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(if (isSos) SOS_NOTIFICATION_ID else System.currentTimeMillis().toInt(), notification)
    }

    override fun onDestroy() {
        runCatching { scanner?.stopScan(scanCallback) }
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        connectionHandlers.values.forEach { it.close() }
        connectionHandlers.clear()
        serverJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "mesh_service_channel"
        private const val SOS_CHANNEL_ID = "sos_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val SOS_NOTIFICATION_ID = 911
    }
}
