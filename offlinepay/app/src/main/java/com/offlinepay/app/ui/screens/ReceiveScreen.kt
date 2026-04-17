package com.offlinepay.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.offlinepay.app.data.model.PaymentToken
import com.offlinepay.app.data.model.TokenPayload
import com.offlinepay.app.data.repo.PaymentRepository
import com.offlinepay.app.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ReceiveUi {
    data object Idle : ReceiveUi
    data class Verified(val token: PaymentToken, val payload: TokenPayload) : ReceiveUi
    data class Error(val msg: String) : ReceiveUi
    data class Done(val accepted: Boolean) : ReceiveUi
}

@HiltViewModel
class ReceiveVm @Inject constructor(
    private val repo: PaymentRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ReceiveUi>(ReceiveUi.Idle)
    val ui: StateFlow<ReceiveUi> = _ui.asStateFlow()

    fun onScanned(raw: String) = viewModelScope.launch {
        _ui.value = when (val r = repo.parseAndVerify(raw)) {
            is PaymentRepository.IncomingResult.Ok        -> ReceiveUi.Verified(r.token, r.payload)
            PaymentRepository.IncomingResult.BadSignature -> ReceiveUi.Error("Invalid signature")
            PaymentRepository.IncomingResult.Expired      -> ReceiveUi.Error("Token expired")
            PaymentRepository.IncomingResult.Duplicate    -> ReceiveUi.Error("Duplicate transaction")
            is PaymentRepository.IncomingResult.Malformed -> ReceiveUi.Error("Malformed: ${r.msg}")
        }
    }

    fun accept(token: PaymentToken, payload: TokenPayload) = viewModelScope.launch {
        val ok = repo.acceptIncoming(token, payload)
        _ui.value = ReceiveUi.Done(accepted = ok)
    }

    fun reject(token: PaymentToken, payload: TokenPayload) = viewModelScope.launch {
        repo.rejectIncoming(token, payload)
        _ui.value = ReceiveUi.Done(accepted = false)
    }

    fun reset() { _ui.value = ReceiveUi.Idle }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReceiveScreen(nav: NavController, vm: ReceiveVm = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val cam = rememberPermissionState(Manifest.permission.CAMERA)

    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(vm::onScanned)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Receive payment") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = ui) {
                ReceiveUi.Idle -> {
                    Text("Scan the sender's QR code.")
                    Button(
                        onClick = {
                            if (cam.status.isGranted) launchScan(launcher)
                            else cam.launchPermissionRequest()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (cam.status.isGranted) "Scan QR" else "Grant camera & scan") }
                }
                is ReceiveUi.Verified -> {
                    val p = s.payload
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Payment from ${p.senderId}", style = MaterialTheme.typography.titleMedium)
                            Text("Amount: ${Money.format(p.amountMinor, p.currency)}")
                            Text("Tx ID: ${p.txId}")
                            Text("Expires at: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(p.expiresAtMillis))}")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { vm.reject(s.token, s.payload) },
                                       modifier = Modifier.weight(1f)) { Text("Reject") }
                        Button(onClick = { vm.accept(s.token, s.payload) },
                               modifier = Modifier.weight(1f)) { Text("Accept") }
                    }
                }
                is ReceiveUi.Error -> {
                    Text(s.msg, color = MaterialTheme.colorScheme.error)
                    Button(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                }
                is ReceiveUi.Done -> {
                    Text(if (s.accepted) "Saved as pending. Will settle when online."
                         else "Transaction not accepted.")
                    Button(onClick = { nav.popBackStack(Routes.HOME, false) },
                           modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }
}

private fun launchScan(launcher: androidx.activity.result.ActivityResultLauncher<ScanOptions>) {
    val opts = ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setBeepEnabled(false)
        setOrientationLocked(true)
        setPrompt("Scan payment QR")
    }
    launcher.launch(opts)
}
