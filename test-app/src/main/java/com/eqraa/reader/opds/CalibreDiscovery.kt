package com.eqraa.reader.opds

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

class CalibreDiscovery(context: Context) {

    // Explicitly declaring type to avoid inference issues.
    // NsdManager is a system service, so we use nullable type to be safe, though usually non-null.
    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    data class DiscoveredServer(val name: String, val host: String, val port: Int) {
        fun toUrl(): String = "$host:$port/opds"
    }

    fun discover(): Flow<List<DiscoveredServer>> = callbackFlow {
        val manager = nsdManager
        
        if (manager == null) {
            Timber.e("NsdManager not available")
            close()
            return@callbackFlow
        }
        
        val foundServices = mutableMapOf<String, DiscoveredServer>()
        
        // Define listener with explicit type
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Timber.d("Service discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Timber.d("Service found: $service")
                // Check if it looks like a calibre or opds service
                if (service.serviceType.contains("calibre") || service.serviceType.contains("opds")) {
                    try {
                        manager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                Timber.e("Resolve failed: $errorCode")
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                Timber.d("Resolve Succeeded. $serviceInfo")
                                val host = serviceInfo.host?.hostAddress
                                val port = serviceInfo.port
                                if (host != null) {
                                    val server = DiscoveredServer(serviceInfo.serviceName, host, port)
                                    foundServices[serviceInfo.serviceName] = server
                                    trySend(foundServices.values.toList())
                                }
                            }
                        })
                    } catch (e: Exception) {
                        Timber.e(e, "Error resolving service: ${service.serviceName}")
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Timber.d("Service lost: $service")
                foundServices.remove(service.serviceName)
                trySend(foundServices.values.toList())
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Timber.d("Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("Discovery failed: Error code $errorCode")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("Stop discovery failed: Error code $errorCode")
            }
        }

        try {
            manager.discoverServices("_calibre._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
             Timber.e(e, "Failed to start discovery")
             close(e)
        }

        awaitClose {
            try {
                // Using reflection to bypass persistent compiler error "Unresolved reference 'stopDiscovery'"
                // This method definitely exists in API 16+
                val method = manager.javaClass.getMethod("stopDiscovery", NsdManager.DiscoveryListener::class.java)
                method.invoke(manager, discoveryListener)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop discovery via reflection")
            }
        }
    }
}
