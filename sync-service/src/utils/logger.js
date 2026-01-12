const winston = require('winston');
const config = require('../config');

const logger = winston.createLogger({
    level: config.logging.level,
    format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.json()
    ),
    transports: [
        // Write all logs with importance level of `error` or less to `error.log`
        // new winston.transports.File({ filename: 'error.log', level: 'error' }),
        // Write all logs to `sync-service.log`
        new winston.transports.File({ filename: config.logging.file }),
    ],
});

// If we're not in production then log to the `console` with the format:
// `${info.level}: ${info.message} JSON.stringify({ ...rest }) `
if (process.env.NODE_ENV !== 'production') {
    logger.add(new winston.transports.Console({
        format: winston.format.combine(
            winston.format.colorize(),
            winston.format.simple()
        ),
    }));
}

module.exports = logger;
