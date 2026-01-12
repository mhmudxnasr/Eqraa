const server = require('./server');
const syncEngine = require('./sync-engine');
const { connectDB, closeDB } = require('./db-client');

// This file is still used for the scheduled sync if running in a managed environment
// but the primary entry point for Railway is src/server.js

exports.scheduledSync = functions.pubsub.schedule('every 60 minutes').onRun(async (context) => {
    console.log('Running scheduled sync...');
    try {
        await connectDB();
        await syncEngine.startSync();
        // You might want to close DB but normally in functions it's okay to leave open for reuse?
        // Actually for a scheduled one-off, closing might be safer to prevent timeout hooks?
        // await closeDB();
    } catch (err) {
        console.error('Scheduled sync failed:', err);
    }
});

