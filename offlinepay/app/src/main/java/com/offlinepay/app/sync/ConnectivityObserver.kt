package com.offlinepay.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers a default network callback. As soon as the device gains an
 * internet-capable network, we enqueue a SettlementWorker run.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    private val ctx: Context
) {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)

    fun start() {
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                SettlementWorker.enqueue(ctx)
            }
        })
    }
}
