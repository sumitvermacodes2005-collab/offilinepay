package com.offlinepay.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object Routes {
    const val HOME = "home"
    const val SEND = "send"
    const val SHOW_QR = "show_qr"
    const val RECEIVE = "receive"
    const val HISTORY = "history"
    const val BLUETOOTH = "bluetooth"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME)      { HomeScreen(nav) }
        composable(Routes.SEND)      { SendScreen(nav) }
        composable(Routes.SHOW_QR)   { ShowQrScreen(nav) }
        composable(Routes.RECEIVE)   { ReceiveScreen(nav) }
        composable(Routes.HISTORY)   { HistoryScreen(nav) }
        composable(Routes.BLUETOOTH) { BluetoothScreen(nav) }
    }
}
