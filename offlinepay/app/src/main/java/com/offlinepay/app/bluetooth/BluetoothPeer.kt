package com.offlinepay.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal classic Bluetooth RFCOMM transport for offline token transfer.
 *
 * Caller is responsible for runtime permissions:
 *   - Android 12+: BLUETOOTH_CONNECT, BLUETOOTH_SCAN
 *   - Older     : BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION
 *
 * Pairing must be done via system Settings before connecting.
 */
@Singleton
class BluetoothPeer @Inject constructor(private val ctx: Context) {

    companion object {
        /** Random fixed UUID identifying our service. Both peers must use it. */
        val SERVICE_UUID: UUID = UUID.fromString("4e8b6b3a-1e58-4f63-9c7e-1c8c0a6e3a11")
        const val SERVICE_NAME = "OfflinePay"
        private const val ACCEPT_TIMEOUT_MS = 60_000
    }

    private val adapter: BluetoothAdapter? =
        ctx.getSystemService(BluetoothManager::class.java)?.adapter

    fun isSupported(): Boolean = adapter != null
    fun isEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> =
        adapter?.bondedDevices?.toList().orEmpty()

    /**
     * Hosts an RFCOMM server, accepts ONE incoming connection (within
     * [ACCEPT_TIMEOUT_MS]), writes [payload] and closes. Returns true on
     * successful transfer.
     */
    @SuppressLint("MissingPermission")
    suspend fun host(payload: String): Boolean = withContext(Dispatchers.IO) {
        val a = adapter ?: return@withContext false
        var server: BluetoothServerSocket? = null
        var socket: BluetoothSocket? = null
        try {
            server = a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
            socket = server.accept(ACCEPT_TIMEOUT_MS)
            socket.outputStream.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            try { socket?.close() } catch (_: Throwable) {}
            try { server?.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Connects to [device] and reads the entire stream as UTF-8 text.
     * Returns the received payload, or null on failure.
     */
    @SuppressLint("MissingPermission")
    suspend fun receiveFrom(device: BluetoothDevice): String? = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            adapter?.cancelDiscovery()
            socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
            socket.connect()
            val text = socket.inputStream.bufferedReader(Charsets.UTF_8).readText()
            text.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        } finally {
            try { socket?.close() } catch (_: Throwable) {}
        }
    }
}
