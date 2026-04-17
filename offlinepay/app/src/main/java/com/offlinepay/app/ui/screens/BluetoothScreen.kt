package com.offlinepay.app.ui.screens

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.offlinepay.app.bluetooth.BluetoothPeer
import com.offlinepay.app.data.repo.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BluetoothVm @Inject constructor(
    private val peer: BluetoothPeer,
    private val repo: PaymentRepository
) : ViewModel() {

    data class UiState(
        val supported: Boolean = true,
        val enabled: Boolean = true,
        val devices: List<BluetoothDevice> = emptyList(),
        val log: String = ""
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refresh() {
        _state.value = _state.value.copy(
            supported = peer.isSupported(),
            enabled = peer.isEnabled(),
            devices = if (peer.isEnabled()) peer.bondedDevices() else emptyList()
        )
    }

    fun host() = viewModelScope.launch {
        val payload = TokenBuffer.value
        if (payload.isNullOrBlank()) {
            log("No token to host. Generate one from Send first.")
            return@launch
        }
        log("Hosting on UUID ${BluetoothPeer.SERVICE_UUID}…\nWaiting for receiver (60s)…")
        val ok = peer.host(payload)
        log(if (ok) "✓ Token sent over Bluetooth." else "✗ Host failed or timed out.")
    }

    fun connect(device: BluetoothDevice) = viewModelScope.launch {
        log("Connecting to ${device.address}…")
        val raw = peer.receiveFrom(device)
        if (raw == null) {
            log("✗ Connection failed.")
            return@launch
        }
        when (val r = repo.parseAndVerify(raw)) {
            is PaymentRepository.IncomingResult.Ok -> {
                val accepted = repo.acceptIncoming(r.token, r.payload)
                log(if (accepted) "✓ Token received and accepted (txId ${r.payload.txId.take(8)}…). Will settle when online."
                    else "Token already in DB.")
            }
            PaymentRepository.IncomingResult.BadSignature -> log("✗ Bad signature.")
            PaymentRepository.IncomingResult.Expired      -> log("✗ Token expired.")
            PaymentRepository.IncomingResult.Duplicate    -> log("✗ Duplicate transaction.")
            is PaymentRepository.IncomingResult.Malformed -> log("✗ Malformed: ${r.msg}")
        }
    }

    private fun log(line: String) {
        _state.value = _state.value.copy(log = (line + "\n" + _state.value.log).take(2_000))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun BluetoothScreen(nav: NavController, vm: BluetoothVm = hiltViewModel()) {
    val state by vm.state.collectAsState()

    val perms = if (Build.VERSION.SDK_INT >= 31) {
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
               Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permState = rememberMultiplePermissionsState(perms)

    LaunchedEffect(permState.allPermissionsGranted) {
        if (permState.allPermissionsGranted) vm.refresh()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Bluetooth transfer") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!permState.allPermissionsGranted) {
                Text("Bluetooth permissions are required.")
                Button(onClick = { permState.launchMultiplePermissionRequest() },
                       modifier = Modifier.fillMaxWidth()) { Text("Grant permissions") }
                return@Scaffold
            }
            if (!state.supported) { Text("Bluetooth not supported on this device."); return@Scaffold }
            if (!state.enabled)   { Text("Enable Bluetooth in system settings, then return."); return@Scaffold }

            Text("Sender: tap Host, then have receiver pick this device.",
                 style = MaterialTheme.typography.bodySmall)
            Button(onClick = vm::host, modifier = Modifier.fillMaxWidth()) {
                Text("Host (broadcast last generated token)")
            }
            Divider()
            Text("Receiver: pick a paired device that is hosting.",
                 style = MaterialTheme.typography.bodySmall)
            if (state.devices.isEmpty()) {
                Text("No bonded devices. Pair them in system Bluetooth settings first.")
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.devices, key = { it.address }) { d ->
                    @Suppress("MissingPermission")
                    val name = try { d.name ?: d.address } catch (_: SecurityException) { d.address }
                    OutlinedButton(onClick = { vm.connect(d) },
                                   modifier = Modifier.fillMaxWidth()) {
                        Text("$name  (${d.address})")
                    }
                }
            }
            Divider()
            Text("Log", style = MaterialTheme.typography.titleSmall)
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Text(
                    state.log.ifBlank { "—" },
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
