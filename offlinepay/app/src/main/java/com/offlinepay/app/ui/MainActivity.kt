package com.offlinepay.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.offlinepay.app.data.crypto.KeyManager
import com.offlinepay.app.sync.ConnectivityObserver
import com.offlinepay.app.ui.screens.AppNav
import com.offlinepay.app.ui.theme.OfflinePayTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var keyManager: KeyManager
    @Inject lateinit var connectivity: ConnectivityObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keyManager.ensureKeyPair()
        connectivity.start()
        setContent { OfflinePayTheme { AppNav() } }
    }
}
