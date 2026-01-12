require('dotenv').config();
const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const db = require('./postgres-db');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = process.env.PORT || 3001;

// Middleware
app.use(cors());
app.use(bodyParser.json());

// Helper for standardized responses
const sendError = (res, status, message, error = null) => {
    if (error) console.error(message, error);
    res.status(status).json({ success: false, error: message });
};

// ============================================
// READING PROGRESS ENDPOINTS
// ============================================

/**
 * POST /api/sync/progress
 * Upsert reading progress with conflict resolution
 */
app.post('/api/sync/progress', async (req, res) => {
    const { userId, bookId, cfi, percentage, timestamp, deviceId } = req.body;

    if (!userId || !bookId || !cfi || percentage === undefined || !timestamp || !deviceId) {
        return sendError(res, 400, 'Missing required fields');
    }

    try {
        await db.query('BEGIN');

        // 1. Fetch existing record via FOR UPDATE to lock row
        const result = await db.query(
            'SELECT * FROM reading_progress WHERE user_id = $1 AND book_id = $2 FOR UPDATE',
            [userId, bookId]
        );
        const existing = result.rows[0];

        let shouldUpdate = false;
        let reason = 'new_record';

        if (!existing) {
            shouldUpdate = true;
        } else {
            const dbTs = parseInt(existing.timestamp);
            const reqTs = parseInt(timestamp);
            const now = Date.now();
            const isDifferentDevice = existing.device_id !== deviceId;
            const isRecent = (now - dbTs) < 5000; // 5 seconds

            // Conflict Resolution Strategy
            if (reqTs > dbTs) {
                // New update is newer in time
                shouldUpdate = true;
                reason = 'newer_timestamp';
            } else if (isDifferentDevice && isRecent) {
                // Existing is recent and from another device (active reading elsewhere)
                // BUT if new update acts as "further progress", we might want to keep it?
                // Plan says: "If local device has very recent unsaved changes, prioritize local"
                // This logic is mostly client-side, but server acts as arbiter.

                // Server rule: If incoming is older, usually reject.
                // Exception: "Furthest progress" fallback for significant jumps?
                if (percentage > existing.percentage) {
                    // It's older but further ahead. This is tricky. 
                    // If it's MUCH further ahead (> 5%), maybe accept it?
                    // For now, stick to strict LWW for consistency, client handles the rest.
                    shouldUpdate = false;
                    reason = 'older_timestamp_conflict';
                } else {
                    shouldUpdate = false;
                    reason = 'older_timestamp';
                }
            } else {
                shouldUpdate = false;
                reason = 'older_timestamp';
            }
        }

        if (shouldUpdate) {
            await db.query(
                `INSERT INTO reading_progress (user_id, book_id, cfi, percentage, timestamp, device_id, updated_at)
                 VALUES ($1, $2, $3, $4, $5, $6, NOW())
                 ON CONFLICT (user_id, book_id) 
                 DO UPDATE SET 
                    cfi = EXCLUDED.cfi,
                    percentage = EXCLUDED.percentage,
                    timestamp = EXCLUDED.timestamp,
                    device_id = EXCLUDED.device_id,
                    updated_at = NOW()`,
                [userId, bookId, cfi, percentage, timestamp, deviceId]
            );
            await db.query('COMMIT');
            res.json({ status: 'updated', reason });
        } else {
            await db.query('COMMIT');
            res.json({
                status: 'ignored',
                reason,
                serverState: {
                    cfi: existing.cfi,
                    percentage: existing.percentage,
                    timestamp: existing.timestamp
                }
            });
        }

    } catch (err) {
        await db.query('ROLLBACK');
        sendError(res, 500, 'Database error', err);
    }
});

/**
 * GET /api/sync/progress/:bookId
 * Get progress for specific book
 */
app.get('/api/sync/progress/:bookId', async (req, res) => {
    const { userId } = req.query; // Authenticated user ID (in real app, extract from token)
    const { bookId } = req.params;

    if (!userId) return sendError(res, 401, 'Unauthorized');

    try {
        const result = await db.query(
            'SELECT cfi, percentage, timestamp, device_id FROM reading_progress WHERE user_id = $1 AND book_id = $2',
            [userId, bookId]
        );

        if (result.rows.length === 0) {
            return res.status(404).json({ error: 'Not found' });
        }

        res.json(result.rows[0]);
    } catch (err) {
        sendError(res, 500, 'Database error', err);
    }
});

/**
 * GET /api/sync/all
 * Batch delta sync
 */
app.get('/api/sync/all', async (req, res) => {
    const { userId, since, limit = 50, page = 1 } = req.query;

    if (!userId) return sendError(res, 401, 'Unauthorized');

    try {
        let query = 'SELECT book_id, cfi, percentage, timestamp, device_id, updated_at FROM reading_progress WHERE user_id = $1';
        const params = [userId];

        if (since) {
            query += ' AND updated_at > $2';
            params.push(new Date(parseInt(since)).toISOString());
        }

        query += ' ORDER BY updated_at DESC LIMIT $' + (params.length + 1) + ' OFFSET $' + (params.length + 2);
        params.push(limit);
        params.push((page - 1) * limit);

        const result = await db.query(query, params);
        res.json(result.rows);
    } catch (err) {
        sendError(res, 500, 'Database error', err);
    }
});

app.listen(PORT, () => {
    console.log(`🚀 PostgreSQL Sync Server running on port ${PORT}`);
});
