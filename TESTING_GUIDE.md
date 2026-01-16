# Testing Guide: Reading Progress Sync Fixes

## 📱 Installation Steps

### 1. Build the APK
```bash
cd /home/mahmud/Documents/Eqraa/Eqraa-main
./gradlew :test-app:assembleDebug
```

### 2. Install on Device
```bash
# Option A: Install directly (overwrites existing app, keeps data)
adb install -r test-app/build/outputs/apk/debug/test-app-debug.apk

# Option B: Fresh install (recommended for testing migration)
adb uninstall com.eqraa.reader
adb install test-app/build/outputs/apk/debug/test-app-debug.apk
```

---

## 🧪 How to Verify Fixes Are Working

### **Test 1: Database Migration Check**

**What to verify:** The `updated_at` column was added successfully

```bash
# Connect to device
adb shell

# Open SQLite database
run-as com.eqraa.reader
cd databases
sqlite3 database

# Check schema
.schema books

# You should see:
# updated_at INTEGER NOT NULL DEFAULT 0
```

**Expected Result:** ✅ Column exists in schema

---

### **Test 2: Timestamp Storage Verification**

**What to verify:** Timestamps are saved to database (not SharedPreferences)

**Steps:**
1. Open the app
2. Open any book
3. Read to page 5
4. Close the app
5. Run this command:

```bash
adb shell "run-as com.eqraa.reader sqlite3 databases/database 'SELECT id, title, updated_at FROM books;'"
```

**Expected Result:** 
```
1|Book Title|1737063600000
```
✅ `updated_at` should be a recent timestamp (not 0)

---

### **Test 3: Local Save Works**

**What to verify:** Progress saves immediately to local database

**Steps:**
1. Open a book
2. Read to page 10
3. **Immediately** force-close the app (don't wait)
   ```bash
   adb shell am force-stop com.eqraa.reader
   ```
4. Reopen the app
5. Open the same book

**Expected Result:** ✅ Book opens at page 10 (progress was saved locally)

---

### **Test 4: Cloud Sync Works**

**What to verify:** Progress syncs to Supabase after debounce (5 seconds)

**Steps:**
1. Open a book
2. Read to page 15
3. Wait 6 seconds (debounce = 5s)
4. Check logs:

```bash
adb logcat -s ReadingSyncManager:D
```

**Expected Logs:**
```
D ReadingSyncManager: ✅ Local save for book 123 at 1737063600000
D ReadingSyncManager: 🔄 Executing debounced sync for book_identifier
D ReadingSyncManager: ✅ Cloud sync successful for book book_identifier
```

**Expected Result:** ✅ You see "Cloud sync successful" after ~5 seconds

---

### **Test 5: Force Sync on App Close**

**What to verify:** Pending syncs execute when app closes

**Steps:**
1. Open a book
2. Read to page 20
3. **Immediately** close the app (within 5 seconds, before debounce)
4. Check logs:

```bash
adb logcat -s ReadingSyncManager:D ReaderViewModel:D
```

**Expected Logs:**
```
D ReaderViewModel: Failed to sync on close (or success message)
D ReadingSyncManager: ⚡ Force syncing 1 pending jobs
D ReadingSyncManager: ✅ Cloud sync successful
```

**Expected Result:** ✅ Sync happens even though you closed before 5 seconds

---

### **Test 6: No Duplicate Saves**

**What to verify:** Opening a book at the same position doesn't update timestamp

**Steps:**
1. Open a book, read to page 25
2. Note the timestamp:
   ```bash
   adb shell "run-as com.eqraa.reader sqlite3 databases/database 'SELECT updated_at FROM books WHERE id=1;'"
   # Output: 1737063600000
   ```
3. Close and reopen the app
4. Open the same book (it opens at page 25, same position)
5. Check timestamp again:
   ```bash
   adb shell "run-as com.eqraa.reader sqlite3 databases/database 'SELECT updated_at FROM books WHERE id=1;'"
   ```

**Expected Result:** ✅ Timestamp is the SAME (no duplicate save)

---

### **Test 7: Sync Status Feedback**

**What to verify:** Sync status is exposed to UI

**Steps:**
1. Open a book
2. Read to page 30
3. Check logs for status changes:

```bash
adb logcat -s ReadingSyncManager:D | grep "SyncStatus"
```

**Expected Logs:**
```
D ReadingSyncManager: SyncStatus: Syncing
D ReadingSyncManager: SyncStatus: Success
```

**Expected Result:** ✅ Status transitions: Idle → Syncing → Success

---

### **Test 8: Conflict Detection (10-Second Window)**

**What to verify:** Conflicts only detected outside 10-second window

**Setup:** You need 2 devices or emulators

**Steps:**
1. **Device A:** Open book, read to page 40, wait 6 seconds
2. **Device B:** Open same book, read to page 50
3. **Device A:** Check if conflict dialog appears

**Expected Result:** 
- ✅ **NO conflict** (both updates within 10-second window)
- Server accepts the newer one (page 50)

**Now test outside window:**
1. **Device A:** Read to page 60, wait 15 seconds
2. **Device B:** Read to page 70
3. **Device A:** Reopen the book

**Expected Result:** 
- ✅ **Conflict dialog appears** (updates > 10 seconds apart)

---

### **Test 9: Offline Mode**

**What to verify:** Progress saves locally when offline, syncs when online

**Steps:**
1. Disable WiFi/Mobile data
2. Open a book, read to page 80
3. Check logs:

```bash
adb logcat -s ReadingSyncManager:D
```

**Expected Logs:**
```
D ReadingSyncManager: ✅ Local save for book 123
W ReadingSyncManager: User not authenticated, skipping cloud sync
```

4. Enable WiFi/Mobile data
5. Wait 6 seconds

**Expected Result:** 
- ✅ Progress saved locally while offline
- ✅ Syncs to cloud when connection restored

---

### **Test 10: Cross-Device Sync**

**What to verify:** Reading on Device A syncs to Device B

**Setup:** 2 devices logged into the same account

**Steps:**
1. **Device A:** Open book, read to page 100, wait 6 seconds
2. **Device B:** Open the app (don't open the book yet)
3. **Device B:** Open the same book

**Expected Result:** 
- ✅ Device B shows a "Jump to page 100?" prompt
- ✅ Accepting the prompt opens book at page 100

---

## 📊 Quick Health Check Commands

### Check Database Schema
```bash
adb shell "run-as com.eqraa.reader sqlite3 databases/database '.schema books'" | grep updated_at
```

### View All Book Progress
```bash
adb shell "run-as com.eqraa.reader sqlite3 databases/database 'SELECT id, title, substr(progression, 1, 50), updated_at FROM books;'"
```

### Monitor Sync Logs (Real-time)
```bash
adb logcat -s ReadingSyncManager:D ReadingProgressRepository:D ReaderViewModel:D
```

### Check Sync Status
```bash
adb logcat -s ReadingSyncManager:D | grep -E "(✅|❌|🔄|⚡)"
```

---

## 🐛 Common Issues & Solutions

### Issue: "Column updated_at doesn't exist"
**Solution:** Migration didn't run. Uninstall and reinstall:
```bash
adb uninstall com.eqraa.reader
adb install test-app/build/outputs/apk/debug/test-app-debug.apk
```

### Issue: "Sync never happens"
**Check:**
1. User logged in? `adb logcat -s SupabaseService:D`
2. Network connected? `adb shell dumpsys connectivity`
3. Debounce timeout? Wait 6+ seconds

### Issue: "Duplicate saves still happening"
**Check:** Hash storage
```bash
adb shell "run-as com.eqraa.reader cat shared_prefs/reader_prefs.xml"
```
Should see `last_locator_hash_X` entries

### Issue: "Conflict dialog appears immediately"
**Check:** Timestamps
```bash
adb shell "run-as com.eqraa.reader sqlite3 databases/database 'SELECT book_id, updated_at FROM books;'"
```
If `updated_at = 0`, progress was never saved

---

## ✅ Success Criteria

All fixes are working if:

- [x] `updated_at` column exists in database
- [x] Timestamps update when reading
- [x] Progress saves locally immediately
- [x] Progress syncs to cloud after 5 seconds
- [x] Force sync happens on app close
- [x] No duplicate saves on reopen
- [x] Sync status changes visible in logs
- [x] Conflicts only detected outside 10s window
- [x] Offline mode saves locally
- [x] Cross-device sync works

---

## 🔍 Advanced Debugging

### Enable Verbose Logging
Add to `Application.kt`:
```kotlin
if (DEBUG) {
    Timber.plant(object : Timber.DebugTree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            super.log(priority, "Eqraa_$tag", message, t)
        }
    })
}
```

### Inspect Supabase Data
1. Open Supabase Dashboard
2. Go to Table Editor → `reading_progress`
3. Check for your user's entries
4. Verify `updated_at` matches local database

### Network Traffic Monitoring
```bash
adb shell tcpdump -i any -s 0 -w /sdcard/capture.pcap
# Then analyze with Wireshark
```

---

## 📞 Need Help?

If any test fails, check:
1. Logcat output for errors
2. Database schema
3. Supabase connection status
4. Network connectivity

All logs should show emoji indicators:
- ✅ = Success
- ❌ = Error
- 🔄 = Syncing
- ⚡ = Force sync
- 🌐 = Remote update
- 📱 = Remote progress detected
