const mongoose = require('mongoose');

const UserSchema = new mongoose.Schema({
    username: { type: String, required: true, unique: true }, // will use 'mahmud'
    settings: {
        fontSize: { type: Number, default: 100 },
        theme: { type: String, default: 'system' },
        fontFamily: String,
        lineHeight: Number,
        margin: Number,
        readingSpeed: Number, // words per minute
        lastUpdated: { type: Number, default: Date.now }
    },
    stats: {
        hoursRead: { type: Number, default: 0 },
        totalBooksRead: { type: Number, default: 0 }
    },
    // Reading Progress: Map for fast access
    // Key: book_id, Value: object
    readingProgress: {
        type: Map,
        of: new mongoose.Schema({
            cfi: String,
            percentage: Number,
            timestamp: Number,
            lastOpened: Number
        }, { _id: false })
    },
    // Library Organization
    collections: [{
        name: String,
        bookIds: [String], // Stable identifiers (ISBN/Hash)
        timestamp: { type: Number, default: Date.now }
    }],
    // Heavy Data: Notes and Highlights
    notes: [{
        bookId: String, // Stable identifier
        content: String,
        cfi: String,
        color: String,
        timestamp: Number,
        note: String
    }]
}, { timestamps: true });

module.exports = mongoose.model('User', UserSchema);
