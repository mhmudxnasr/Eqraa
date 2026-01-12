const mongoose = require('mongoose');

const UserSchema = new mongoose.Schema({
    _id: {
        type: String,
        default: 'Mahmud' // Hardcoded as requested
    },
    lastBackup: { type: Date, default: Date.now },
    settings: {
        type: Map,
        of: mongoose.Schema.Types.Mixed,
        default: {}
    },
    stats: {
        type: Map,
        of: mongoose.Schema.Types.Mixed,
        default: {}
    },
    books: [{
        bookId: String,
        title: String,
        author: String,
        href: String,
        highlights: [Object], // Store notes/highlights nested here
        bookmarks: [Object],
        progress: Object
    }]
}, {
    minimize: false, // Ensure empty objects are saved
    timestamps: true
});

module.exports = mongoose.model('UserBackup', UserSchema);
