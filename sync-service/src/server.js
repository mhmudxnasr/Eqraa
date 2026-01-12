require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const bodyParser = require('body-parser');
const multer = require('multer');
const path = require('path');
const { v4: uuidv4 } = require('uuid');
const User = require('./models/User');
const CloudBook = require('./models/CloudBook');

// Helper to get GridFS bucket
let bucket;
const getBucket = () => {
    if (!bucket) {
        // Use GridFSBucket directly from mongoose.mongo to avoid version conflicts
        bucket = new mongoose.mongo.GridFSBucket(mongoose.connection.db, { bucketName: 'books' });
    }
    return bucket;
};

// Firebase removed - Using MongoDB GridFS instead

const app = express();
const AUTH_TOKEN = "234267";

// Configure multer for MEMORY storage (Serverless friendly)
const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: 100 * 1024 * 1024 }, // 100MB limit
    fileFilter: (req, file, cb) => {
        const allowedTypes = ['.epub', '.pdf', '.mobi', '.cbz'];
        const ext = path.extname(file.originalname).toLowerCase();
        if (allowedTypes.includes(ext)) {
            cb(null, true);
        } else {
            cb(new Error(`File type ${ext} not allowed`));
        }
    }
});

// Middleware
app.use(cors({ origin: true }));
app.use(bodyParser.json({ limit: '50mb' }));

// Database Connection
const connectDB = async () => {
    if (mongoose.connection.readyState === 0) {
        const uri = process.env.MONGODB_URI || process.env.DB_CONNECTION_STRING;
        if (!uri) {
            console.error('Error: MONGODB_URI or DB_CONNECTION_STRING not set!');
            throw new Error('Database connection string missing');
        }

        console.log('Connecting to MongoDB...');
        await mongoose.connect(uri, {
            serverSelectionTimeoutMS: 5000, // Fail fast if DB down
            socketTimeoutMS: 45000,
        });
        console.log('MongoDB Connected');
    }
};

// Health Check Endpoint (for cloud monitoring)
app.get('/health', (req, res) => {
    res.json({ status: 'ok', timestamp: new Date().toISOString(), mode: 'serverless' });
});

// Root Endpoint
app.get('/', (req, res) => {
    res.send('Eqraa Sync Service v1.0.0');
});

app.use(async (req, res, next) => {
    try {
        await connectDB();
        next();
    } catch (err) {
        console.error('DB Connection error:', err);
        res.status(500).json({ error: 'Database connection failed' });
    }
});

// Auth Middleware
const authenticate = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader || req.body.auth;
    if (token === AUTH_TOKEN) {
        next();
    } else {
        res.status(401).json({ error: 'Unauthorized' });
    }
};

// ============================================
// READING PROGRESS ROUTES
// ============================================

app.get('/sync-position', authenticate, async (req, res) => {
    try {
        let user = await User.findOne({ username: 'mahmud' });
        if (!user) {
            user = new User({ username: 'mahmud', readingProgress: {} });
            await user.save();
        }
        res.json(user.readingProgress);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/sync-position', authenticate, async (req, res) => {
    const { bookId, cfi, percentage, timestamp } = req.body;

    if (!bookId || !cfi || !timestamp) {
        return res.status(400).json({ error: 'Missing required fields' });
    }

    try {
        let user = await User.findOne({ username: 'mahmud' });
        if (!user) {
            user = new User({ username: 'mahmud', readingProgress: {} });
        }

        const currentProgress = user.readingProgress.get(bookId);

        if (currentProgress && currentProgress.timestamp >= timestamp) {
            return res.json({ status: 'ignored', serverTimestamp: currentProgress.timestamp });
        }

        user.readingProgress.set(bookId, { cfi, percentage, timestamp, lastOpened: Date.now() });
        await user.save();

        res.json({ status: 'updated', timestamp });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ============================================
// SETTINGS & PREFERENCES ROUTES
// ============================================

app.get('/get-preferences', authenticate, async (req, res) => {
    try {
        const user = await User.findOne({ username: 'mahmud' });
        res.json(user ? user.settings : {});
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/sync-preferences', authenticate, async (req, res) => {
    const { settings } = req.body;
    if (!settings) return res.status(400).json({ error: 'Missing settings' });

    try {
        const user = await User.findOne({ username: 'mahmud' });
        if (!user) return res.status(404).json({ error: 'User not found' });

        // LWW for settings
        if (settings.lastUpdated && user.settings.lastUpdated >= settings.lastUpdated) {
            return res.json({ status: 'ignored', serverTimestamp: user.settings.lastUpdated });
        }

        user.settings = { ...user.settings.toObject(), ...settings };
        user.settings.lastUpdated = Date.now();
        await user.save();

        res.json({ status: 'updated', lastUpdated: user.settings.lastUpdated });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ============================================
// COLLECTIONS ROUTES (Library Organization)
// ============================================

app.get('/get-collections', authenticate, async (req, res) => {
    try {
        const user = await User.findOne({ username: 'mahmud' });
        res.json(user ? user.collections : []);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/sync-collections', authenticate, async (req, res) => {
    const { collections } = req.body;
    if (!collections) return res.status(400).json({ error: 'Missing collections' });

    try {
        const user = await User.findOneAndUpdate(
            { username: 'mahmud' },
            { collections },
            { new: true }
        );
        res.json({ status: 'success', collections: user.collections });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post('/full-backup', authenticate, async (req, res) => {
    const { settings, stats, readingProgress, notes, collections } = req.body;

    try {
        const updateData = { settings, stats, notes, collections };
        if (readingProgress) {
            updateData.readingProgress = readingProgress;
        }

        const user = await User.findOneAndUpdate(
            { username: 'mahmud' },
            updateData,
            { new: true, upsert: true }
        );

        res.json({ status: 'success', user });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ============================================
// CLOUD LIBRARY ROUTES (Firebase Storage)
// ============================================

app.post('/upload-book', authenticate, upload.single('book'), async (req, res) => {
    try {
        if (!req.file) {
            return res.status(400).json({ error: 'No file uploaded' });
        }

        const { title, author, identifier, mediaType } = req.body;
        const storedFilename = `${uuidv4()}${path.extname(req.file.originalname)}`;

        // Upload to GridFS
        const uploadStream = getBucket().openUploadStream(storedFilename, {
            contentType: mediaType || 'application/octet-stream',
            metadata: {
                originalName: req.file.originalname,
                title: title,
                author: author
            }
        });

        const uploadPromise = new Promise((resolve, reject) => {
            uploadStream.on('finish', resolve);
            uploadStream.on('error', reject);
            uploadStream.end(req.file.buffer);
        });

        await uploadPromise;

        const cloudBook = new CloudBook({
            userId: 'mahmud',
            filename: req.file.originalname,
            storedFilename: storedFilename, // Pure filename, not path
            title: title || req.file.originalname,
            author: author || '',
            identifier: identifier || '',
            mediaType: mediaType || 'application/octet-stream',
            fileSize: req.file.size
        });

        await cloudBook.save();
        console.log(`Book uploaded to GridFS: ${cloudBook.filename}`);

        res.json({
            status: 'uploaded',
            id: cloudBook._id,
            filename: cloudBook.filename,
            size: cloudBook.fileSize
        });
    } catch (err) {
        console.error('Upload Error:', err);
        res.status(500).json({ error: `Server Upload Error: ${err.message}` });
    }
});

app.get('/library', authenticate, async (req, res) => {
    try {
        const books = await CloudBook.find({ userId: 'mahmud' })
            .select('-storedFilename')
            .sort({ uploadedAt: -1 });
        res.json(books);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/download-book/:id', authenticate, async (req, res) => {
    try {
        const book = await CloudBook.findOne({ _id: req.params.id, userId: 'mahmud' });
        if (!book) return res.status(404).json({ error: 'Book not found' });

        // Find file in GridFS
        const files = await getBucket().find({ filename: book.storedFilename }).toArray();
        if (files.length === 0) return res.status(404).json({ error: 'File not found in storage' });

        res.set('Content-Type', book.mediaType || 'application/octet-stream');
        res.set('Content-Disposition', `attachment; filename="${book.filename}"`);

        const downloadStream = getBucket().openDownloadStreamByName(book.storedFilename);
        downloadStream.pipe(res);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.delete('/delete-book/:id', authenticate, async (req, res) => {
    try {
        const book = await CloudBook.findOneAndDelete({ _id: req.params.id, userId: 'mahmud' });
        if (!book) return res.status(404).json({ error: 'Book not found' });

        // Delete from GridFS
        if (book.storedFilename) {
            const files = await getBucket().find({ filename: book.storedFilename }).toArray();
            for (const file of files) {
                try {
                    await getBucket().delete(file._id);
                } catch (e) {
                    console.warn(`Failed to delete GridFS file ${file._id}:`, e.message);
                }
            }
        }

        res.json({ status: 'deleted', id: book._id });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Start Server
app.listen(process.env.PORT || 3000, () => {
    console.log(`Server running on port ${process.env.PORT || 3000}`);
});
