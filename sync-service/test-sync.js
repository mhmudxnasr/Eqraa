const { v4: uuidv4 } = require('uuid');
const LZString = require('lz-string');

// ==========================================
// MOCKS FOR NODE.JS ENVIRONMENT
// ==========================================

// Mock IDB
const mockDB = {
    store: new Map(),
    get: async (storeName, key) => mockDB.store.get(`${storeName}:${key}`),
    put: async (storeName, val, key) => {
        const k = key || val.bookId || val.deviceId; // infer key
        mockDB.store.set(`${storeName}:${k}`, val);
    },
    getAll: async () => Array.from(mockDB.store.values())
};

// Mock fetch
global.fetch = async (url, options) => {
    console.log(`[NETWORK] ${options ? options.method : 'GET'} ${url}`);

    if (url.includes('/api/sync/progress') && options.method === 'POST') {
        const body = JSON.parse(options.body);
        // Simulate server response
        return {
            ok: true,
            json: async () => ({ status: 'updated', timestamp: Date.now() })
        };
    }
    return { ok: true, json: async () => ([]) };
};

// ==========================================
// SYNC MANAGER (Adapted for JS/Node Test)
// ==========================================
// In a real app, this would be the compiled output of SyncManager.ts
// We replicate the logic here to verify the ALGORITHM.

class SyncManagerTest {
    constructor(userId, apiBaseUrl) {
        this.userId = userId;
        this.apiBaseUrl = apiBaseUrl;
        this.syncQueue = new Set();
        this.debounceTimer = null;

        // Init device ID mock
        this.deviceId = uuidv4();
    }

    async saveProgress(bookId, cfi, percentage) {
        const compressedCfi = LZString.compressToUTF16(cfi);
        const timestamp = Date.now();

        const record = {
            userId: this.userId,
            bookId,
            cfi: compressedCfi,
            percentage,
            timestamp,
            deviceId: this.deviceId,
            isSynced: false
        };

        // Save to "DB"
        await mockDB.put('reading_progress', record);

        // Queue Sync
        this.queueSync(bookId);

        return { success: true, timestamp };
    }

    async getProgress(bookId) {
        const record = await mockDB.get('reading_progress', bookId);
        if (record) {
            return {
                ...record,
                cfi: LZString.decompressFromUTF16(record.cfi)
            };
        }
        return null;
    }

    queueSync(bookId) {
        this.syncQueue.add(bookId);
        if (this.debounceTimer) clearTimeout(this.debounceTimer);

        console.log(`[DEBOUNCE] Timer started for ${bookId}`);
        this.debounceTimer = setTimeout(() => this.flushSyncQueue(), 2000);
    }

    async flushSyncQueue() {
        console.log('[SYNC] Flushing queue:', Array.from(this.syncQueue));
        if (this.syncQueue.size === 0) return;

        const bookIds = Array.from(this.syncQueue);
        this.syncQueue.clear();

        for (const bookId of bookIds) {
            const record = await mockDB.get('reading_progress', bookId);
            if (!record) continue;

            try {
                const response = await fetch(`${this.apiBaseUrl}/api/sync/progress`, {
                    method: 'POST',
                    body: JSON.stringify({
                        userId: this.userId,
                        bookId: record.bookId,
                        cfi: record.cfi,
                        percentage: record.percentage,
                        timestamp: record.timestamp,
                        deviceId: record.deviceId
                    })
                });

                if (response.ok) {
                    console.log(`[SYNC] Success for ${bookId}`);
                }
            } catch (e) {
                console.error('Sync error', e);
            }
        }
    }
}

// ==========================================
// RUN TESTS
// ==========================================

async function runTests() {
    console.log('--- STARTING SYNC LOGIC TESTS ---');

    const userId = uuidv4();
    const syncManager = new SyncManagerTest(userId, 'http://localhost:3001');

    // TEST 1: Compression
    console.log('\nTest 1: Save & Compression');
    const originalCfi = '/6/4[chap01]!/4/2/1:0';
    await syncManager.saveProgress('book-1', originalCfi, 0.1);

    const loaded = await syncManager.getProgress('book-1');
    if (loaded.cfi === originalCfi) {
        console.log('✅ CFI matches after decompression');
    } else {
        console.error('❌ CFI mismatch');
    }

    // TEST 2: Debouncing
    console.log('\nTest 2: Debouncing');
    console.log('Triggering multiple saves...');

    // Fire 3 saves quickly
    syncManager.saveProgress('book-2', '/6/2!', 0.1);
    syncManager.saveProgress('book-2', '/6/4!', 0.2);
    syncManager.saveProgress('book-2', '/6/6!', 0.3);

    console.log('Waiting 2.5s...');
    await new Promise(r => setTimeout(r, 2500));
    // Expect only ONE network call in logs (or at least one flush)

    console.log('\n--- TESTS COMPLETED ---');
}

runTests();
