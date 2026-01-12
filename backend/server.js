require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const UserBackup = require('./models/UserBackup');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json({ limit: '50mb' })); // Large limit for book data

// Connect to MongoDB Atlas
mongoose.connect(process.env.MONGODB_URI)
    .then(() => console.log('✅ Connected to MongoDB Atlas'))
    .catch(err => console.error('❌ MongoDB connection error:', err));

// Root route for verification
app.get('/', (req, res) => {
    res.send('🚀 Eqraa Sync Server is running!');
});

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// ==================== BACKUP ENDPOINTS ====================

// Get user backup data
app.get('/api/backup/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        const backup = await UserBackup.findById(userId);

        if (!backup) {
            return res.status(404).json({ error: 'Backup not found' });
        }

        res.json(backup);
    } catch (error) {
        console.error('Error fetching backup:', error);
        res.status(500).json({ error: 'Failed to fetch backup' });
    }
});

// Create or update user backup
app.put('/api/backup/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        const backupData = req.body;

        const backup = await UserBackup.findByIdAndUpdate(
            userId,
            {
                ...backupData,
                _id: userId,
                lastBackup: new Date()
            },
            { upsert: true, new: true, setDefaultsOnInsert: true }
        );

        res.json({ success: true, backup });
    } catch (error) {
        console.error('Error saving backup:', error);
        res.status(500).json({ error: 'Failed to save backup' });
    }
});

// ==================== READING POSITION SYNC ====================

// Get reading position for a specific book
app.get('/api/position/:userId/:bookId', async (req, res) => {
    try {
        const { userId, bookId } = req.params;
        const backup = await UserBackup.findById(userId);

        if (!backup) {
            return res.status(404).json({ error: 'User not found' });
        }

        const book = backup.books.find(b => b.bookId === bookId);
        if (!book || !book.progress) {
            return res.status(404).json({ error: 'Position not found' });
        }

        res.json(book.progress);
    } catch (error) {
        console.error('Error fetching position:', error);
        res.status(500).json({ error: 'Failed to fetch position' });
    }
});

// Update reading position for a specific book (lightweight endpoint)
app.patch('/api/position/:userId/:bookId', async (req, res) => {
    try {
        const { userId, bookId } = req.params;
        const { cfi, percentage, timestamp } = req.body;

        // Find and update in one operation
        const result = await UserBackup.findOneAndUpdate(
            { _id: userId, 'books.bookId': bookId },
            {
                $set: {
                    'books.$.progress': { cfi, percentage, timestamp: timestamp || Date.now() }
                }
            },
            { new: true }
        );

        if (!result) {
            // Book doesn't exist, add it
            await UserBackup.findByIdAndUpdate(
                userId,
                {
                    $push: {
                        books: {
                            bookId,
                            progress: { cfi, percentage, timestamp: timestamp || Date.now() }
                        }
                    }
                },
                { upsert: true }
            );
        }

        res.json({ success: true });
    } catch (error) {
        console.error('Error updating position:', error);
        res.status(500).json({ error: 'Failed to update position' });
    }
});

// Update user statistics
app.patch('/api/stats/:userId', async (req, res) => {
    try {
        const { userId } = req.params;
        const stats = req.body;

        await UserBackup.findByIdAndUpdate(
            userId,
            { $set: { stats, lastBackup: new Date() } },
            { upsert: true }
        );

        res.json({ success: true });
    } catch (error) {
        console.error('Error updating stats:', error);
        res.status(500).json({ error: 'Failed to update stats' });
    }
});

// ==================== HIGHLIGHTS & BOOKMARKS ====================

// Add highlight to a book
app.post('/api/highlight/:userId/:bookId', async (req, res) => {
    try {
        const { userId, bookId } = req.params;
        const highlight = req.body;

        await UserBackup.findOneAndUpdate(
            { _id: userId, 'books.bookId': bookId },
            { $push: { 'books.$.highlights': highlight } },
            { upsert: true }
        );

        res.json({ success: true });
    } catch (error) {
        console.error('Error adding highlight:', error);
        res.status(500).json({ error: 'Failed to add highlight' });
    }
});

// Add bookmark to a book
app.post('/api/bookmark/:userId/:bookId', async (req, res) => {
    try {
        const { userId, bookId } = req.params;
        const bookmark = req.body;

        await UserBackup.findOneAndUpdate(
            { _id: userId, 'books.bookId': bookId },
            { $push: { 'books.$.bookmarks': bookmark } },
            { upsert: true }
        );

        res.json({ success: true });
    } catch (error) {
        console.error('Error adding bookmark:', error);
        res.status(500).json({ error: 'Failed to add bookmark' });
    }
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Eqraa Backend running on port ${PORT}`);
    console.log(`🏠 Local: http://localhost:${PORT}`);
    console.log(`📱 Network: http://192.168.1.2:${PORT}`);
});
