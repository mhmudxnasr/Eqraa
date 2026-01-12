package com.eqraa.reader.data

/**
 * Centralized sync server configuration.
 * 
 * IMPORTANT: Update SYNC_SERVER_URL after deploying to Railway/Render.
 */
object SyncConfig {
    /**
     * Sync server base URL.
     * 
     * Options:
     * - Local dev (emulator): "http://10.0.2.2:3000/"
     * - Local dev (device): "http://YOUR_PC_IP:3000/"
     * - Cloud (Railway): "https://YOUR-APP-NAME.up.railway.app/"
     */
    const val SYNC_SERVER_URL = "https://eqraa-production.up.railway.app/"
    
    /**
     * Authentication token for API access.
     */
    const val AUTH_TOKEN = "234267"
}
