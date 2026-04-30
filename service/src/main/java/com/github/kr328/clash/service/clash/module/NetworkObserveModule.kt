package com.github.kr328.clash.service.clash.module

import android.app.Service
import android.net.*
import android.os.Build
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.util.asSocketAddressText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class NetworkObserveModule(service: Service) : Module<Network>(service) {
    private val connectivity = service.getSystemService<ConnectivityManager>()!!
    private val networks: Channel<Network> = Channel(Channel.UNLIMITED)
    private val networkChanges: Channel<Unit> = Channel(Channel.CONFLATED)
    private val request = NetworkRequest.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)
        }
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }.build()

    private data class NetworkInfo(
        @Volatile var losingMs: Long = 0,
        @Volatile var dnsList: List<InetAddress> = emptyList()
    ) {
        fun isAvailable(): Boolean = losingMs < System.currentTimeMillis()
    }

    private val networkInfos = ConcurrentHashMap<Network, NetworkInfo>()

    @Volatile
    private var curDnsList = emptyList<String>()

    @Volatile
    private var curNetworkKey: String? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i("NetworkObserve onAvailable network=$network")
            networkInfos[network] = NetworkInfo()
            notifyNetworkChangedDebounced()

            networks.trySend(network)
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            Log.i("NetworkObserve onLosing network=$network")
            networkInfos[network]?.losingMs = System.currentTimeMillis() + maxMsToLive
            // Treat possible network loss as a recovery signal early. This may close
            // active connections before the network is fully lost, but it avoids stale
            // app-side flows lingering across real network switches.
            notifyNetworkChangedDebounced()

            networks.trySend(network)
        }

        override fun onLost(network: Network) {
            Log.i("NetworkObserve onLost network=$network")
            networkInfos.remove(network)
            notifyNetworkChangedDebounced()

            networks.trySend(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            Log.i("NetworkObserve onLinkPropertiesChanged network=$network $linkProperties")
            networkInfos[network]?.dnsList = linkProperties.dnsServers
            notifyNetworkChangedDebounced()

            networks.trySend(network)
        }

        override fun onUnavailable() {
            Log.i("NetworkObserve onUnavailable")
        }
    }

    private fun notifyNetworkChangedDebounced() {
        networkChanges.trySend(Unit)
    }

    private fun register(): Boolean {
        Log.i("NetworkObserve start register")
        return try {
            connectivity.registerNetworkCallback(request, callback)

            true
        } catch (e: Exception) {
            Log.w("NetworkObserve register failed", e)

            false
        }
    }

    private fun unregister(): Boolean {
        Log.i("NetworkObserve start unregister")
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w("NetworkObserve unregister failed", e)
        }

        return false
    }

    private fun networkToInt(entry: Map.Entry<Network, NetworkInfo>): Int {
        val capabilities = connectivity.getNetworkCapabilities(entry.key)
        // calculate priority based on transport type, available state
        // lower value means higher priority
        // wifi > ethernet > usb tethering > bluetooth tethering > cellular > satellite > other
        return when {
            capabilities == null -> 100
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> 90
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> 2
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 3
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 4
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE) -> 5
            // TRANSPORT_LOWPAN / TRANSPORT_THREAD / TRANSPORT_WIFI_AWARE are not for general internet access, which will not set as default route.
            else -> 20
        } + (if (entry.value.isAvailable()) 0 else 10)
    }

    private fun currentDnsList(): List<String> {
        return (networkInfos.asSequence().minByOrNull { networkToInt(it) }?.value?.dnsList
            ?: emptyList()).map { x -> x.asSocketAddressText(53) }
    }

    private fun currentNetworkKey(): String? {
        val entry = networkInfos.asSequence().minByOrNull { networkToInt(it) } ?: return null
        val capabilities = connectivity.getNetworkCapabilities(entry.key) ?: return null
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> "usb"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE) -> "satellite"
            else -> "other"
        }

        return "${entry.key}@$transport"
    }

    private fun notifyDnsChange(force: Boolean = false) {
        val dnsList = currentDnsList()
        val prevDnsList = curDnsList
        val notifyList = if (dnsList.isNotEmpty()) dnsList else prevDnsList
        if (dnsList.isNotEmpty()) {
            curDnsList = dnsList
        }

        if (force || (notifyList.isNotEmpty() && prevDnsList != notifyList)) {
            Log.i("notifyDnsChange force=$force $prevDnsList -> $notifyList")
            Clash.notifyDnsChanged(notifyList)
        }
    }

    private fun notifyNetworkRecoveryIfChanged() {
        val networkKey = currentNetworkKey()
        val prevNetworkKey = curNetworkKey
        val networkChanged = networkKey != null && networkKey != prevNetworkKey

        if (networkKey != null) {
            curNetworkKey = networkKey
        }

        Log.i("NetworkObserve recovery networkChanged=$networkChanged $prevNetworkKey -> $networkKey")
        notifyDnsChange(force = networkChanged)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun run() {
        register()

        try {
            var pendingNetworkRecovery = false
            while (true) {
                select<Unit> {
                    networks.onReceive {
                        enqueueEvent(it)
                    }

                    networkChanges.onReceive {
                        pendingNetworkRecovery = true
                    }

                    if (pendingNetworkRecovery) {
                        onTimeout(NETWORK_CHANGE_DEBOUNCE_MS) {
                            pendingNetworkRecovery = false
                            notifyNetworkRecoveryIfChanged()
                        }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                unregister()

                Log.i("NetworkObserve dns = []")
                // Service shutdown intentionally clears Android-provided system DNS.
                // NotifyDnsChanged also closes active connections, which is harmless here
                // because the VPN/core runtime is already being torn down.
                Clash.notifyDnsChanged(emptyList())
            }
        }
    }

    companion object {
        private const val NETWORK_CHANGE_DEBOUNCE_MS = 2000L
    }
}
