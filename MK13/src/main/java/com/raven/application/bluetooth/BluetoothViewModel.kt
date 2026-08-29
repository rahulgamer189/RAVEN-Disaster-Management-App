package com.raven.application.bluetooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.raven.application.R
import com.raven.application.data.MeshProtocol
import com.raven.application.data.RavenPreferences

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@SuppressLint("MissingPermission")
class BluetoothViewModel(
    application: Application
) : AndroidViewModel(application) {

        private val context: Context = application.applicationContext
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val _displayName = MutableStateFlow(RavenPreferences.readDisplayName(context))
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>> = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceDomain>> = _pairedDevices.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val connectedDevices: StateFlow<List<BluetoothDeviceDomain>> = _connectedDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BluetoothDeviceDomain?>(null)
    val selectedDevice: StateFlow<BluetoothDeviceDomain?> = _selectedDevice.asStateFlow()

    private val _messages = MutableStateFlow<List<BluetoothMessage>>(emptyList())
    val messages: StateFlow<List<BluetoothMessage>> = _messages.asStateFlow()

    private val _peerTelemetry = MutableStateFlow<Map<String, BluetoothMessage>>(emptyMap())
    val peerTelemetry: StateFlow<Map<String, BluetoothMessage>> = _peerTelemetry.asStateFlow()

    private val _camps = MutableStateFlow<List<Camp>>(emptyList())
    val camps: StateFlow<List<Camp>> = _camps.asStateFlow()

    private val _navigationTarget = MutableStateFlow<BluetoothMessage?>(null)
    val navigationTarget: StateFlow<BluetoothMessage?> = _navigationTarget.asStateFlow()

    private val _ownLocation = MutableStateFlow<TelemetryData?>(null)
    val ownLocation: StateFlow<TelemetryData?> = _ownLocation.asStateFlow()

    private var bluetoothService: BluetoothService? = null
    private var isServiceBound = false
    private val telemetryProvider = TelemetryProvider(context)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.BluetoothBinder
            bluetoothService = binder.getService()
            isServiceBound = true
            
            // Auto-start mesh discovery in the service once bound
            bluetoothService?.startDiscovery()

            // Observe messages from service
            bluetoothService?.messages?.onEach { message ->
                _messages.update { it + message }
                if (message.senderName != context.getString(R.string.label_sender_me)) {
                    _peerTelemetry.update { it + (message.senderName to message) }
                }
                
                if (message.messageType == MeshProtocol.CAMP_UPSERT) {
                    val parts = message.message.split("|")
                    if (parts.size >= 3) {
                        try {
                            val camp = Camp(
                                name = parts[0],
                                latitude = parts[1].toDouble(),
                                longitude = parts[2].toDouble(),
                                version = parts.getOrNull(3)?.split("=")?.getOrNull(1)?.toLong() ?: System.currentTimeMillis()
                            )
                            _camps.update { current ->
                                val existing = current.find { it.name == camp.name }
                                if (existing == null || existing.version < camp.version) {
                                    (current.filter { it.name != camp.name } + camp).sortedBy { it.name }
                                } else current
                            }
                        } catch (e: Exception) {
                            Log.e("BluetoothViewModel", "Failed to parse camp message: ${message.message}", e)
                        }
                    }
                }
            }?.launchIn(viewModelScope)

            // Observe connected devices from service
            bluetoothService?.connectedDevices?.onEach { devices ->
                _connectedDevices.value = devices
                _isConnected.value = devices.isNotEmpty()
            }?.launchIn(viewModelScope)
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            bluetoothService = null
            isServiceBound = false
        }
    }

    private val deviceFoundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val domainDevice = it.toDomain()
                        if (domainDevice.address.startsWith("02:00:00")) return@let // Skip hidden addresses
                        
                        Log.d("BluetoothViewModel", "Device found: ${domainDevice.name} (${domainDevice.address})")
                        _scannedDevices.update { devices ->
                            if (devices.any { d -> d.address == domainDevice.address }) {
                                // Update name if it was previously unknown
                                devices.map { d -> if (d.address == domainDevice.address) d.copy(name = domainDevice.name ?: d.name) else d }
                            } else devices + domainDevice
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d("BluetoothViewModel", "Discovery started")
                    _isScanning.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d("BluetoothViewModel", "Discovery finished")
                    _isScanning.value = false
                }
            }
        }
    }

    init {
        updatePairedDevices()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(deviceFoundReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(deviceFoundReceiver, filter)
        }

        viewModelScope.launch {
            while (isActive) {
                try {
                    val telemetry = telemetryProvider.getTelemetry(highAccuracy = false)
                    _ownLocation.value = telemetry
                } catch (e: Exception) {
                    Log.e("BluetoothViewModel", "Error fetching own telemetry", e)
                }
                delay(30_000) // Update every 30 seconds
            }
        }
    }

    fun startService() {
        if (isServiceBound) return
        
        // Start and bind to service
        val intent = Intent(context, BluetoothService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun startScanning() {
        Log.d("BluetoothViewModel", "Requesting start scanning (BLE + Classic)")
        _scannedDevices.value = emptyList()
        
        // Trigger BLE discovery in the service
        bluetoothService?.startDiscovery()
        
        // Also trigger classic discovery for older nodes or backup
        val started = bluetoothAdapter?.startDiscovery() ?: false
        if (!started) {
            Log.e("BluetoothViewModel", "Failed to start classic discovery")
        }
        // Even if classic fails, BLE might still be running in service
        _isScanning.value = true
    }

    fun stopScanning() {
        Log.d("BluetoothViewModel", "Requesting stop scanning")
        bluetoothAdapter?.cancelDiscovery()
        _isScanning.value = false
    }

    fun updatePairedDevices() {
        val devices = bluetoothAdapter?.bondedDevices?.map { it.toDomain(isPaired = true) } ?: emptyList()
        _pairedDevices.value = devices
    }

    fun startServer() {
        // Server is now managed by the service
    }

    fun connectToDevice(device: BluetoothDeviceDomain) {
        Log.d("BluetoothViewModel", "Connecting to device via service: ${device.address}")
        _selectedDevice.value = device
        bluetoothService?.connectToDevice(device.address)
    }

    fun disconnect() {
        // For multi-peer, we might want to disconnect a specific device or all.
        _messages.value = emptyList()
    }

        fun updateDisplayName(value: String) {
        val normalized = value.trim().take(48)
        _displayName.value = normalized
        RavenPreferences.writeDisplayName(context, normalized)
        bluetoothService?.updateSenderName(normalized)
    }

    fun sendMessage(text: String, type: String = MeshProtocol.CHAT) {

        if (text.isBlank()) return
        bluetoothService?.sendMessage(text, type)
    }

    fun shareLocation() {
        bluetoothService?.shareLocation()
    }

    fun saveCamp(name: String, lat: Double, lon: Double) {
        val messageBody = "$name|$lat|$lon|version=${System.currentTimeMillis()}"
        sendMessage(messageBody, type = MeshProtocol.CAMP_UPSERT)

        // Also update local state immediately
        val camp = Camp(name, lat, lon)
        _camps.update { current ->
            (current.filter { it.name != name } + camp).sortedBy { it.name }
        }
    }

    fun navigateTo(message: BluetoothMessage?) {
        _navigationTarget.value = message
    }

    fun clearNavigation() {
        _navigationTarget.value = null
    }


    override fun onCleared() {
        super.onCleared()
        Log.d("BluetoothViewModel", "onCleared - cleaning up")
                runCatching { context.unregisterReceiver(deviceFoundReceiver) }
        bluetoothService?.stopScanning()
        if (isServiceBound) {

            context.unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
