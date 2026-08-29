package com.raven.application.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.raven.application.data.MeshProtocol

import java.io.IOException
import java.util.UUID

class BluetoothServer(
    private val adapter: BluetoothAdapter,
    private val uuid: UUID = MeshProtocol.serviceUuid

) {
    private var serverSocket: BluetoothServerSocket? = null

    @SuppressLint("MissingPermission")
    suspend fun listen(onConnection: (BluetoothSocket) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("BluetoothServer", "Starting server socket listener with UUID: $uuid")
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
                
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("Raven", uuid)
                while (true) {
                    Log.d("BluetoothServer", "Waiting for next client connection...")
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        Log.d("BluetoothServer", "Client connected: ${socket.remoteDevice?.address}")
                        onConnection(socket)
                    } else {
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e("BluetoothServer", "Error in server socket listener", e)
            } finally {
                Log.d("BluetoothServer", "Closing server socket")
                serverSocket?.close()
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
