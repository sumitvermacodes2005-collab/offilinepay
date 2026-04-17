package com.offlinepay.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.offlinepay.app.qr.QrCodec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowQrScreen(nav: NavController) {
    val ctx = LocalContext.current
    val tokenJson = TokenBuffer.value
    val bmp = remember(tokenJson) { tokenJson?.let { QrCodec.encode(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Show to receiver") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Payment QR",
                    modifier = Modifier.size(280.dp)
                )
                Text("Recipient scans this QR. Token expires in 5 min.")
                OutlinedButton(onClick = { nav.navigate(Routes.BLUETOOTH) },
                               modifier = Modifier.fillMaxWidth()) { Text("Send via Bluetooth") }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("smsto:")
                        putExtra("sms_body", tokenJson)
                    }
                    ctx.startActivity(send)
                }, modifier = Modifier.fillMaxWidth()) { Text("Send via SMS") }
                Button(onClick = { nav.popBackStack(Routes.HOME, false) },
                       modifier = Modifier.fillMaxWidth()) { Text("Done") }
            } else {
                Text("No token to display. Go back and generate one first.")
            }
        }
    }
}
