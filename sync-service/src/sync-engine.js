const { getFirestore } = require('./firebase-client');
const { getDB } = require('./db-client');
const config = require('./config');
const logger = require('./utils/logger');

// Collections map - map local collection names to Firebase paths
// This assumes the local MongoDB has collections named 'books', 'stats', etc.
// You can adjust these based on your actual MongoDB collection names.
const COLLECTIONS = {
    BOOKS: 'books',
    SESSIONS: 'reading_sessions', // Adjustable based on actual DB
    BOOKMARKS: 'bookmarks',
    HIGHLIGHTS: 'highlights'
};

class SyncEngine {
    constructor() {
        this.firestore = getFirestore();
        this.userId = config.sync.targetUserId;
    }

    async startSync() {
        if (!this.userId) {
            logger.error('Target User ID not set. Cannot sync.');
            return;
        }

        logger.info(`Starting sync for user: ${this.userId}`);
        const localDB = getDB();

        try {
            await this.syncBooks(localDB);
            await this.syncStats(localDB);
            await this.syncUserData(localDB);

            // Update last sync timestamp (optional, could be stored in a 'meta' collection)
            logger.info('Sync cycle completed successfully.');
        } catch (error) {
            logger.error('Error during sync cycle:', error);
        }
    }

    async syncBooks(db) {
        logger.debug('Syncing books...');
        const books = await db.collection(COLLECTIONS.BOOKS).find({}).toArray();

        if (books.length === 0) return;

        const batch = this.firestore.batch();
        let count = 0;

        for (const book of books) {
            // Create a reference: users/{userId}/library/books/{bookId}
            // Assuming book._id is the unique identifier
            const ref = this.firestore
                .collection('users').doc(this.userId)
                .collection('library').doc('books')
                .collection('items').doc(String(book._id)); // storing in subcollection 'items' for cleaner structure

            // Clean up data if necessary (remove _id or convert types)
            const { _id, ...bookData } = book;
            bookData.syncedAt = new Date();

            batch.set(ref, bookData, { merge: true });
            count++;
        }

        if (count > 0) {
            await batch.commit();
            logger.info(`Synced ${count} books.`);
        }
    }

    async syncStats(db) {
        logger.debug('Syncing stats...');
        // Assuming 'reading_sessions' contains session logs
        const sessions = await db.collection(COLLECTIONS.SESSIONS).find({}).toArray();

        if (sessions.length > 0) {
            // Aggregate or just sync sessions? 
            // Let's sync sessions as a subcollection: users/{userId}/stats/sessions/{sessionId}
            const batch = this.firestore.batch();
            let count = 0;

            for (const session of sessions) {
                const ref = this.firestore
                    .collection('users').doc(this.userId)
                    .collection('stats').doc('sessions')
                    .collection('history').doc(String(session._id));

                const { _id, ...sessionData } = session;
                sessionData.syncedAt = new Date();
                batch.set(ref, sessionData, { merge: true });
                count++;
            }
            if (count > 0) {
                await batch.commit();
                logger.info(`Synced ${count} reading sessions.`);
            }
        }

        // Also sync aggregate stats if available (e.g. total reading time)
        // This part depends heavily on if you have a pre-calculated stats table
    }


    async syncUserData(db) {
        logger.debug('Syncing user data (bookmarks, highlights)...');

        // Sync Bookmarks
        const bookmarks = await db.collection(COLLECTIONS.BOOKMARKS).find({}).toArray();
        if (bookmarks.length > 0) {
            const batch = this.firestore.batch();
            for (const bm of bookmarks) {
                const ref = this.firestore
                    .collection('users').doc(this.userId)
                    .collection('userData').doc('bookmarks')
                    .collection('items').doc(String(bm._id));

                const { _id, ...bmData } = bm;
                batch.set(ref, bmData, { merge: true });
            }
            await batch.commit();
            logger.info(`Synced ${bookmarks.length} bookmarks.`);
        }

        // Sync Highlights
        const highlights = await db.collection(COLLECTIONS.HIGHLIGHTS).find({}).toArray();
        if (highlights.length > 0) {
            const batch = this.firestore.batch();
            for (const hl of highlights) {
                const ref = this.firestore
                    .collection('users').doc(this.userId)
                    .collection('userData').doc('highlights')
                    .collection('items').doc(String(hl._id));

                const { _id, ...hlData } = hl;
                batch.set(ref, hlData, { merge: true });
            }
            await batch.commit();
            logger.info(`Synced ${highlights.length} highlights.`);
        }
    }
}

module.exports = new SyncEngine();
