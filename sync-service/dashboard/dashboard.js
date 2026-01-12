/**
 * Eqraa Sync Dashboard Logic
 * Handles real-time updates and data fetching from the PostgreSQL backend.
 */

class DashboardManager {
    constructor() {
        this.apiBaseUrl = 'http://localhost:3001';
        this.userId = 'mahmud'; // Hardcoded for demo, would come from auth
        this.init();
    }

    async init() {
        console.log('Initialize Dashboard...');
        this.refreshData();

        // Start polling for updates
        setInterval(() => this.refreshData(), 30000); // Pulse every 30s
    }

    async refreshData() {
        try {
            await Promise.all([
                this.fetchActivityLog(),
                this.fetchRecentBooks()
            ]);
            document.getElementById('last-sync-time').innerText = `Last update: ${new Date().toLocaleTimeString()}`;
        } catch (err) {
            console.error('Error refreshing dashboard data:', err);
        }
    }

    async fetchActivityLog() {
        // Mock data for demo - in production this would hit /api/sync/all
        const logContainer = document.getElementById('activity-log');
        // Simulate fetching...
    }

    async fetchRecentBooks() {
        try {
            const response = await fetch(`${this.apiBaseUrl}/api/sync/all?userId=${this.userId}&limit=5`);
            if (!response.ok) return;

            const books = await response.json();
            this.renderBooks(books);
        } catch (err) {
            console.warn('Could not fetch real books mapping, using demo data.');
        }
    }

    renderBooks(books) {
        const carousel = document.getElementById('book-carousel');
        if (!books || books.length === 0) return;

        // Clear and render real data
        // ... implementation for production
    }
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    window.dashboard = new DashboardManager();
});
