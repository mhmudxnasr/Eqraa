require('dotenv').config();
const path = require('path');

module.exports = {
    firebase: {
        projectId: process.env.FIREBASE_PROJECT_ID || 'eqraa-2faf5',
        serviceAccountPath: process.env.FIREBASE_SERVICE_ACCOUNT_PATH || './serviceAccountKey.json'
    },
    db: {
        connectionString: process.env.DB_CONNECTION_STRING,
        name: process.env.DB_NAME || 'eqraa_db'
    },
    sync: {
        intervalSeconds: parseInt(process.env.SYNC_INTERVAL_SECONDS || '30', 10),
        targetUserId: process.env.TARGET_USER_ID
    },
    logging: {
        level: process.env.LOG_LEVEL || 'info',
        file: path.join(__dirname, '../sync-service.log')
    }
};
