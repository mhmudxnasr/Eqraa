const { MongoClient } = require('mongodb');
const config = require('./config');
const logger = require('./utils/logger');

let client;
let db;

const connectDB = async () => {
    if (db) return db;

    try {
        if (!config.db.connectionString) {
            throw new Error('DB_CONNECTION_STRING is not defined in configuration');
        }

        client = new MongoClient(config.db.connectionString);
        await client.connect();

        db = client.db(config.db.name);
        logger.info(`Connected to MongoDB: ${config.db.name}`);
        return db;
    } catch (error) {
        logger.error('Failed to connect to MongoDB:', error);
        throw error;
    }
};

const getDB = () => {
    if (!db) {
        throw new Error('Database not initialized. Call connectDB() first.');
    }
    return db;
};

const closeDB = async () => {
    if (client) {
        await client.close();
        logger.info('MongoDB connection closed');
        client = null;
        db = null;
    }
};

module.exports = {
    connectDB,
    getDB,
    closeDB
};
