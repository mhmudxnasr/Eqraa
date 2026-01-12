const { Pool } = require('pg');

// Configuration should come from environment variables
const config = {
    user: process.env.PG_USER || 'postgres',
    host: process.env.PG_HOST || 'localhost',
    database: process.env.PG_DATABASE || 'eqraa',
    password: process.env.PG_PASSWORD || 'postgres',
    port: parseInt(process.env.PG_PORT || '5432'),
    // Connection pool settings
    max: 20, // Max clients in pool
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 2000,
};

const pool = new Pool(config);

// Logging
pool.on('error', (err, client) => {
    console.error('Unexpected error on idle client', err);
});

module.exports = {
    /**
     * Execute a query against the pool
     * @param {string} text - SQL query text
     * @param {any[]} params - Query parameters
     */
    query: async (text, params) => {
        const start = Date.now();
        try {
            const res = await pool.query(text, params);
            const duration = Date.now() - start;
            // Log slow queries (> 100ms)
            if (duration > 100) {
                console.log('Executed query', { text, duration, rows: res.rowCount });
            }
            return res;
        } catch (error) {
            console.error('Query Error', { text, error });
            throw error;
        }
    },

    /**
     * Get a client from the pool for transactions
     */
    getClient: async () => {
        const client = await pool.connect();
        const query = client.query;
        const release = client.release;

        // Monkey patch the query method to keep track of the last query executed
        const timeout = 5000;
        const lastQuery = { text: '', params: [] };

        client.query = (...args) => {
            client.lastQuery = args;
            return query.apply(client, args);
        };

        client.release = () => {
            // clear our timeout
            client.query = query;
            client.release = release;
            return release.apply(client);
        };

        return client;
    },

    pool
};
