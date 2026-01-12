const admin = require('firebase-admin');
const config = require('./config');
const logger = require('./utils/logger');
const fs = require('fs');

let isInitialized = false;

const initFirebase = () => {
    if (isInitialized) return admin;

    try {
        if (fs.existsSync(config.firebase.serviceAccountPath)) {
            const serviceAccount = require(config.firebase.serviceAccountPath);
            admin.initializeApp({
                credential: admin.credential.cert(serviceAccount)
            });
            logger.info('Firebase initialized with service account.');
        } else {
            logger.warn(`Service account file not found at ${config.firebase.serviceAccountPath}. Attempting default credentials...`);
            // Fallback to default credentials (useful for GCP environments)
            admin.initializeApp({
                projectId: config.firebase.projectId
            });
        }
        isInitialized = true;
    } catch (error) {
        logger.error('Failed to initialize Firebase:', error);
        throw error;
    }

    return admin;
};

const getFirestore = () => {
    if (!isInitialized) initFirebase();
    return admin.firestore();
};

module.exports = {
    initFirebase,
    getFirestore
};
