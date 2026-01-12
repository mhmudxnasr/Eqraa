const mongoose = require('mongoose');

const CloudBookSchema = new mongoose.Schema({
    userId: { type: String, required: true, default: 'mahmud' },
    filename: { type: String, required: true },         // Original filename
    storedFilename: { type: String, required: true },   // UUID-based server filename
    title: { type: String },
    author: { type: String },
    identifier: { type: String },                       // Book identifier (ISBN, etc.)
    mediaType: { type: String },                        // application/epub+zip, application/pdf
    fileSize: { type: Number },
    uploadedAt: { type: Date, default: Date.now }
}, { timestamps: true });

// Compound index for userId + identifier for fast lookups
CloudBookSchema.index({ userId: 1, identifier: 1 });

module.exports = mongoose.model('CloudBook', CloudBookSchema);
