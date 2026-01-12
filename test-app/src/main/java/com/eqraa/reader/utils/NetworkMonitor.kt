/*
 * Network Monitor Utility
 * 
 * Provides network connectivity state as a StateFlow for reactive network awareness.
 * Uses ConnectivityManager.NetworkCallback for real-time connectivity changes.
 */

package com.eqraa.reader.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Monitors network connectivity state and exposes it as a StateFlow.
 * 
 * Usage:
 * ```kotlin
 * val networkMonitor = NetworkMonitor(context)
 * 
 * // Check current state
 * if (networkMonitor.isOnline.value) { ... }
 * 
 * // Observe changes
 * networkMonitor.isOnline.collect { isOnline ->
 *     if (isOnline) retryPendingOperations()
 * }
 * ```
 */
class NetworkMonitor(context: Context) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
        as ConnectivityManager
    
    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    
    /**
     * StateFlow indicating whether the device has internet connectivity.
     * Emits true when connected, false when disconnected.
     */
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.d("Network available")
            _isOnline.value = true
        }
        
        override fun onLost(network: Network) {
            Timber.d("Network lost")
            _isOnline.value = checkCurrentConnectivity()
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            ) && networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )
            
            if (_isOnline.value != hasInternet) {
                Timber.d("Network capabilities changed: hasInternet=$hasInternet")
                _isOnline.value = hasInternet
            }
        }
    }
    
    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Timber.d("NetworkMonitor registered, initial state: ${_isOnline.value}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register network callback")
        }
    }
    
    /**
     * Check current connectivity state synchronously.
     */
    private fun checkCurrentConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Unregister callback when no longer needed (e.g., in Application.onTerminate)
     */
    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Timber.d("NetworkMonitor unregistered")
        } catch (e: Exception) {
            Timber.e(e, "Failed to unregister network callback")
        }
    }
}
