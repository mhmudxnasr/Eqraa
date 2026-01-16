#!/bin/bash

# Eqraa Reading Progress Sync - Install & Test Script
# This script installs the app and runs basic verification tests

set -e  # Exit on error

echo "🚀 Eqraa Reading Progress Sync - Installation & Testing"
echo "========================================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
APK_PATH="test-app/build/outputs/apk/debug/test-app-debug.apk"
PACKAGE_NAME="com.eqraa.reader"

# Check if device is connected
echo -e "${BLUE}📱 Checking for connected device...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ No device connected. Please connect a device or start an emulator.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Device connected${NC}"
echo ""

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}❌ APK not found at $APK_PATH${NC}"
    echo -e "${YELLOW}Building APK...${NC}"
    ./gradlew :test-app:assembleDebug
fi

# Ask user about installation type
echo -e "${YELLOW}Installation Options:${NC}"
echo "1. Fresh install (recommended for testing migration)"
echo "2. Update install (keeps existing data)"
read -p "Choose option (1 or 2): " install_option

if [ "$install_option" == "1" ]; then
    echo -e "${BLUE}🗑️  Uninstalling existing app...${NC}"
    adb uninstall $PACKAGE_NAME 2>/dev/null || echo "App not installed"
    echo -e "${BLUE}📦 Installing fresh...${NC}"
    adb install $APK_PATH
else
    echo -e "${BLUE}📦 Updating app...${NC}"
    adb install -r $APK_PATH
fi

echo -e "${GREEN}✅ Installation complete!${NC}"
echo ""

# Run basic tests
echo -e "${BLUE}🧪 Running basic verification tests...${NC}"
echo ""

# Test 1: Check database migration
echo -e "${YELLOW}Test 1: Database Migration${NC}"
SCHEMA=$(adb shell "run-as $PACKAGE_NAME sqlite3 databases/database '.schema books'" 2>/dev/null | grep -c "updated_at" | tr -d '\r')
if [ -z "$SCHEMA" ]; then SCHEMA="0"; fi
if [ "$SCHEMA" -gt "0" ]; then
    echo -e "${GREEN}✅ Database migration successful - updated_at column exists${NC}"
else
    echo -e "${RED}❌ Database migration failed - updated_at column missing${NC}"
    echo -e "${YELLOW}   Try: Fresh install (option 1)${NC}"
fi
echo ""

# Test 2: Check if app starts
echo -e "${YELLOW}Test 2: App Launch${NC}"
echo -e "${BLUE}Launching app...${NC}"
echo "Starting: Intent { cmp=com.eqraa.reader/.MainActivity }"
adb shell am start -n "com.eqraa.reader/.MainActivity"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ App launched successfully${NC}"
else
    echo -e "${RED}❌ App failed to launch${NC}"
    echo -e "${YELLOW}   Check logcat for errors: adb logcat -s AndroidRuntime:E${NC}"
fi
echo ""

# Test 3: Monitor sync logs
echo -e "${YELLOW}Test 3: Sync System Check${NC}"
echo -e "${BLUE}Monitoring logs for 5 seconds...${NC}"
timeout 5 adb logcat -s ReadingSyncManager:D Application:D 2>/dev/null | head -20 || true
echo ""

# Summary
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✅ Installation Complete!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Open a book in the app"
echo "2. Read a few pages"
echo "3. Monitor sync logs:"
echo -e "   ${BLUE}adb logcat -s ReadingSyncManager:D${NC}"
echo ""
echo "4. Check database:"
echo -e "   ${BLUE}adb shell \"run-as $PACKAGE_NAME sqlite3 databases/database 'SELECT id, title, updated_at FROM books;'\"${NC}"
echo ""
echo -e "${YELLOW}Full Testing Guide:${NC}"
echo "See TESTING_GUIDE.md for comprehensive tests"
echo ""
echo -e "${GREEN}Happy Testing! 🎉${NC}"
