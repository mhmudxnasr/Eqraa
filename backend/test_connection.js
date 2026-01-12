require('dotenv').config();
const mongoose = require('mongoose');
console.log('Testing connection to:', process.env.MONGODB_URI ? 'URI PRESENT' : 'URI MISSING');
mongoose.connect(process.env.MONGODB_URI)
    .then(() => {
        console.log('✅ Connected');
        process.exit(0);
    })
    .catch(err => {
        console.error('❌ Error:', err);
        process.exit(1);
    });
