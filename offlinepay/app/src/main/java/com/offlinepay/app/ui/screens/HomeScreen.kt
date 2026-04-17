package com.offlinepay.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("OfflinePay") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Send & receive even when offline.", style = MaterialTheme.typography.bodyLarge)

            Button(onClick = { nav.navigate(Routes.SEND) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text("Send payment")
            }
            OutlinedButton(onClick = { nav.navigate(Routes.RECEIVE) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text("Receive (scan QR)")
            }
            OutlinedButton(onClick = { nav.navigate(Routes.BLUETOOTH) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text("Bluetooth transfer")
            }
            TextButton(onClick = { nav.navigate(Routes.HISTORY) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text("Transaction history")
            }
        }
    }
}
