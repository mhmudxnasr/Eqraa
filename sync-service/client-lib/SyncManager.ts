import { openDB, IDBPDatabase } from 'idb';
import LZString from 'lz-string';
import { v4 as uuidv4 } from 'uuid';

interface ReadingProgress {
    userId: string;
    bookId: string;
    cfi: string;
    percentage: number;
    timestamp: number;
    deviceId: string;
    isSynced: boolean;
}

interface SyncResponse {
    status: 'updated' | 'ignored' | 'conflict';
    reason?: string;
    serverState?: {
        cfi: string;
        percentage: number;
        timestamp: number;
    };
}

export class SyncManager {
    private dbName = 'StartReadingDB';
    private storeName = 'reading_progress';
    private deviceIdStoreName = 'device_info';
    private deviceId: string | null = null;
    private dbPromise: Promise<IDBPDatabase>;
    private syncQueue: Set<string> = new Set();
    private debounceTimer: NodeJS.Timeout | null = null;
    private userId: string;
    private apiBaseUrl: string;

    constructor(userId: string, apiBaseUrl: string) {
        this.userId = userId;
        this.apiBaseUrl = apiBaseUrl;

        this.dbPromise = openDB(this.dbName, 1, {
            upgrade(db) {
                if (!db.objectStoreNames.contains('reading_progress')) {
                    const store = db.createObjectStore('reading_progress', { keyPath: 'bookId' });
                    store.createIndex('isSynced', 'isSynced');
                }
                if (!db.objectStoreNames.contains('device_info')) {
                    db.createObjectStore('device_info');
                }
            },
        });

        this.initDeviceId();
    }

    private async initDeviceId() {
        const db = await this.dbPromise;
        let id = await db.get(this.deviceIdStoreName, 'deviceId');
        if (!id) {
            id = uuidv4();
            await db.put(this.deviceIdStoreName, id, 'deviceId');
        }
        this.deviceId = id;
        console.log(`Initialized SyncManager with Device ID: ${this.deviceId}`);
    }

    /**
     * Public API: Save progress locally and queue sync
     */
    async saveProgress(bookId: string, cfi: string, percentage: number) {
        if (!this.deviceId) await this.initDeviceId();

        const compressedCfi = LZString.compressToUTF16(cfi);
        const timestamp = Date.now();

        const record: ReadingProgress = {
            userId: this.userId,
            bookId,
            cfi: compressedCfi,
            percentage,
            timestamp,
            deviceId: this.deviceId!,
            isSynced: false
        };

        const db = await this.dbPromise;
        await db.put(this.storeName, record);

        // Queue network sync
        this.queueSync(bookId);

        return { success: true, timestamp };
    }

    /**
     * Get local progress
     */
    async getProgress(bookId: string) {
        const db = await this.dbPromise;
        const record = await db.get(this.storeName, bookId);
        if (record) {
            return {
                ...record,
                cfi: LZString.decompressFromUTF16(record.cfi)
            };
        }
        return null;
    }

    // ==========================================
    // SYNC LOGIC
    // ==========================================

    private queueSync(bookId: string) {
        this.syncQueue.add(bookId);

        // Debounce: Cancel previous timer
        if (this.debounceTimer) {
            clearTimeout(this.debounceTimer);
        }

        // Set new timer (2 seconds)
        this.debounceTimer = setTimeout(() => {
            this.flushSyncQueue();
        }, 2000);
    }

    private async flushSyncQueue() {
        if (this.syncQueue.size === 0) return;

        const bookIds = Array.from(this.syncQueue);
        this.syncQueue.clear();

        const db = await this.dbPromise;

        for (const bookId of bookIds) {
            const record: ReadingProgress = await db.get(this.storeName, bookId);
            if (!record) continue;

            try {
                const response = await fetch(`${this.apiBaseUrl}/api/sync/progress`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        userId: this.userId,
                        bookId: record.bookId,
                        cfi: record.cfi, // Already compressed
                        percentage: record.percentage,
                        timestamp: record.timestamp,
                        deviceId: record.deviceId
                    })
                });

                if (response.ok) {
                    const data: SyncResponse = await response.json();

                    if (data.status === 'updated') {
                        record.isSynced = true;
                        await db.put(this.storeName, record);
                        console.log(`Synced ${bookId}: Success`);
                    } else if (data.status === 'ignored' && data.serverState) {
                        // Server rejected our update (we are outdated)
                        // Should we update local?
                        // If reason is 'older_timestamp', we should probably accept server state
                        console.warn(`Synced ${bookId}: Ignored by server (${data.reason})`);
                        // Optionally update local DB if server has better data
                    }
                } else {
                    console.error(`Sync failed for ${bookId}: ${response.statusText}`);
                    // Re-queue for retry (can add exponential backoff here)
                    this.syncQueue.add(bookId);
                }
            } catch (err) {
                console.error(`Sync network error for ${bookId}`, err);
                this.syncQueue.add(bookId);
            }
        }
    }

    /**
     * Poll for updates from other devices
     */
    startSyncPolling(intervalMs: number = 15000) {
        setInterval(() => this.fetchLatestFromServer(), intervalMs);
    }

    async fetchLatestFromServer() {
        // TODO: Implement delta sync with 'since' parameter
        // For now, fetching all relevant books (could be optimized)
        try {
            // Example: Get all changes since last known sync? 
            // Simplifying: Just fetch batch of recent updates
            const response = await fetch(`${this.apiBaseUrl}/api/sync/all?userId=${this.userId}&limit=20`);
            if (!response.ok) return;

            const serverRecords = await response.json();
            const db = await this.dbPromise;

            for (const serverRecord of serverRecords) {
                const localRecord = await db.get(this.storeName, serverRecord.book_id);

                let shouldApply = false;

                if (!localRecord) {
                    shouldApply = true;
                } else {
                    const serverTs = parseInt(serverRecord.timestamp);
                    const localTs = localRecord.timestamp;

                    // Conflict Resolution (Client Side)
                    if (serverTs > localTs) {
                        // Server is newer. 
                        // Check if local is "active" (< 5s old)
                        const isLocalActive = (Date.now() - localTs) < 5000;
                        if (isLocalActive) {
                            console.log(`Ignoring server update for ${serverRecord.book_id}: Local is active`);
                            shouldApply = false;
                        } else {
                            shouldApply = true;
                        }
                    }
                }

                if (shouldApply) {
                    await db.put(this.storeName, {
                        userId: this.userId,
                        bookId: serverRecord.book_id,
                        cfi: serverRecord.cfi, // Already compressed
                        percentage: serverRecord.percentage,
                        timestamp: parseInt(serverRecord.timestamp),
                        deviceId: serverRecord.device_id, // From other device
                        isSynced: true
                    });
                    console.log(`Applied server update for ${serverRecord.book_id}`);
                    // Trigger UI update event here
                }
            }
        } catch (err) {
            console.error('Polling error', err);
        }
    }
}
