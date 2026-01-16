# Eqraa Reader v4.0.0

**Eqraa** is a premium, open-source reading application reimagined for modern Android devices. Built with a focus on immersive design, powerful synchronization, and privacy, Eqraa provides the ultimate reading experience for EPUB, PDF, and Audiobooks.

## What's New in v4.0.0

### 🔄 Smart Sync System (V2)
- **Robust Conflict Resolution**: New high-performance engine to detect and resolve reading progress mismatches between devices.
- **Premium Design**: Redesigned Conflict Resolution Dialog with a "Glassy Black & White" aesthetic.
- **Reading Analytics Sync**: Your reading sessions (duration, frequency, and time) now sync automatically to the cloud via Supabase.
- **Safe Handshake**: Improved logic to ensure "Last Read" positions are never accidentally overwritten during device switching.

### 📐 Advanced Typography & Layout
- **Fixed Spacing Controls**: Line spacing and Word spacing are now fully functional.
- **Enhanced RTL Support**: Fine-tuned typography specifically for Arabic books, ensuring a premium reading experience for all languages.
- **"Book Styles" Toggle**: Complete control over your reading view. Choose between the original publisher's layout or your own custom settings.

### 🧠 Advanced AI Personas
- **Specialized Modes**: Choose from **English Explanation**, **Arabic Explanation**, and **Real Life Scenario** to tailor the AI's assistance to your needs.
- **Visual Identity**: Distinct icons for each AI mode help you easily identify the active persona.

### 🌍 Enhanced Translation
- **Context-Rich**: Translations now include usage examples to help you fully understand the context.
- **Reliability**: Improved integration with Groq API for faster and more accurate results.

### 🎨 Refined UI/UX
- **Visual Clarity**: Updated iconography and smoother transitions throughout the app.
- **Jump-to-Latest**: Smart snackbar notifications when newer progress is merged from other devices.


## Technical Highlights
- **Engine**: Built on the robust **Eqraa Toolkit** (formerly Readium Kotlin).
- **Architecture**: Modern MVVM with Jetpack Compose UI.
- **Privacy**: No tracking or analytics. Your library data belongs to you.

## Installation
Download the latest APK from the [Releases](https://github.com/mhmudxnasr/Eqraa/releases) page.

---
*Developed by [Mahmud Nasr](https://github.com/mhmudxnasr). Licensed under BSD-3.*
