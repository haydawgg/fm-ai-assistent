# Electron Desktop App Migration Plan

Convert FM AI Assistant from a web browser app to a standalone desktop application using Electron.

## Overview

This plan outlines the steps to wrap the existing Spring Boot backend + React/Vaadin frontend into an Electron desktop app, allowing users to run FM AI Assistant as a native desktop application on Windows, macOS, and Linux.

**Current Architecture:**
- Spring Boot REST API running on `http://127.0.0.1:8080`
- React + Vaadin frontend served by Spring Boot
- Users access via web browser

**Target Architecture:**
- Electron app that bundles and manages the Java backend
- Same React + Vaadin frontend
- Desktop app with taskbar icon, installer, auto-updates

---

## Phase 1: Setup & Project Structure

### 1.1 Create Electron Configuration
- [ ] Create `electron/` directory at project root
- [ ] Create `electron/main.js` - Main Electron process
- [ ] Create `electron/preload.js` - Preload script for IPC
- [ ] Create `electron/assets/` - App icons, splash screen

### 1.2 Update Root Configuration
- [ ] Create `electron-builder.config.js` - Build configuration for packaging
- [ ] Update `package.json` (root level) with Electron dependencies and build scripts
- [ ] Create `.electronignore` - Files to exclude from Electron package
- [ ] Update `.gitignore` - Ignore Electron build artifacts

### 1.3 Java Backend Integration
- [ ] Create `electron/java-launcher.js` - Module to spawn and manage Java process
- [ ] Add logic to detect if Java is installed
- [ ] Add logic to bundle/locate the JAR file in the app
- [ ] Implement graceful shutdown of Java process when app closes

---

## Phase 2: Core Electron Implementation

### 2.1 Main Process (electron/main.js)
- [ ] Import required Electron modules (app, BrowserWindow, ipcMain)
- [ ] Define app lifecycle handlers (app.on('ready'), app.on('quit'))
- [ ] Create BrowserWindow configuration (size, preload, webPreferences)
- [ ] Implement Java backend startup logic
- [ ] Implement error handling for backend failures
- [ ] Add window management (minimize, maximize, close)

### 2.2 Preload Script (electron/preload.js)
- [ ] Create IPC bridge for secure communication between frontend and Electron
- [ ] Expose safe API methods (getAppVersion, openExternalLink, etc)

### 2.3 Java Launcher Module (electron/java-launcher.js)
- [ ] Detect Java installation
- [ ] Locate JAR file (built with Maven)
- [ ] Spawn Java process with appropriate JAR/native-image
- [ ] Implement health check polling
- [ ] Handle process errors and output logging
- [ ] Gracefully kill Java process on app exit

---

## Phase 3: Build & Packaging

### 3.1 Maven Configuration
- [ ] Update pom.xml
- [ ] Add maven-assembly-plugin to create standalone JAR
- [ ] Add profile to copy JAR to electron/resources/ during build
- [ ] Ensure JAR is executable/portable

### 3.2 Electron Builder Configuration
- [ ] Create electron-builder.config.js
- [ ] Windows build settings (NSIS installer, portable EXE)
- [ ] macOS build settings (DMG, code signing)
- [ ] Linux build settings (AppImage, deb)
- [ ] Define app metadata (name, version, description)

### 3.3 Build Scripts
- [ ] Add npm scripts to package.json
- [ ] electron-dev: Start Electron in dev mode
- [ ] electron-build: Build Electron app
- [ ] build-all: Build Maven + create installers
- [ ] build-win, build-mac, build-linux: Platform-specific builds

---

## Phase 4: Frontend Integration (Optional Enhancements)

### 4.1 Context Menu
- [ ] Implement right-click context menu
- [ ] Add copy/paste/select-all for text fields
- [ ] Add debugging options

### 4.2 App Menu
- [ ] Create native menu bar
- [ ] Add preferences/settings menu item
- [ ] Add About dialog
- [ ] Add keyboard shortcuts

### 4.3 Tray Icon (Optional)
- [ ] Add system tray icon
- [ ] Minimize-to-tray functionality
- [ ] Right-click menu with quick actions

---

## Phase 5: Testing & QA

### 5.1 Development Testing
- [ ] Test launching from source with electron-dev
- [ ] Verify Java backend starts and loads
- [ ] Test all UI pages load correctly
- [ ] Test FM26 data loading from RAM
- [ ] Test chat with OpenRouter
- [ ] Verify graceful shutdown

### 5.2 Build Testing
- [ ] Test production builds for Windows
- [ ] Test production builds for macOS
- [ ] Test production builds for Linux
- [ ] Verify installer works on clean systems
- [ ] Verify app shortcuts/icons are created

### 5.3 Edge Cases
- [ ] FM26 not running (error handling)
- [ ] Java not installed (helpful error message)
- [ ] Port 8080 already in use (error handling)
- [ ] Network errors during AI calls
- [ ] Graceful handling of backend crashes

---

## Phase 6: Distribution & Auto-Updates (Optional)

### 6.1 Auto-Updates Setup
- [ ] Integrate electron-updater
- [ ] Set up release server or GitHub Releases
- [ ] Implement update checking on app startup
- [ ] Add update progress UI

### 6.2 Installer Customization
- [ ] Add custom installer screen (branding, license)
- [ ] Configure installation directory
- [ ] Add desktop shortcut option
- [ ] Add start menu integration (Windows)

---

## Deliverables

By end of Phase 3, you'll have:
- electron/ directory with main process, preload, and Java launcher
- electron-builder.config.js for packaging
- Updated package.json with build scripts
- Updated pom.xml for Maven integration
- Working desktop installers for Windows/macOS/Linux
- Single bundled app that users can install and run

---

## Dependencies to Add

```json
{
  "devDependencies": {
    "electron": "^latest",
    "electron-builder": "^latest",
    "electron-updater": "^latest"
  }
}
```

---

## File Structure After Completion

```
fm-ai-assistent/
├── electron/
│   ├── main.js
│   ├── preload.js
│   ├── java-launcher.js
│   ├── assets/
│   │   ├── icon.png
│   │   ├── icon.ico
│   │   └── icon.icns
│   └── resources/
│       └── fm-ai-assistent-*.jar
├── electron-builder.config.js
├── package.json
├── pom.xml
├── src/
└── ELECTRON_MIGRATION_PLAN.md
```

---

## Development Workflow

### First Time Setup
```bash
npm install
mvn clean package
npm run electron-dev
```

### Subsequent Development
```bash
# Make frontend changes, refresh in Electron (Ctrl+R)
# Make backend changes, rebuild: mvn package
```

### Build for Release
```bash
npm run build-all
# Or specific platform
npm run build-win
npm run build-mac
npm run build-linux
```

---

## Known Considerations

1. **Java Runtime**: Users need Java 25 installed, OR bundle a JRE (~200MB size increase)
2. **Port Conflicts**: If port 8080 is already in use, add fallback port logic
3. **Code Signing**: macOS/Windows code signing for distribution requires certificates
4. **Update Strategy**: Auto-updates require a release server
5. **Permissions**: FM26 process access may require same Windows user/Admin privileges

---

## Next Steps

1. Start with Phase 1 & 2 to establish core Electron setup
2. Test with Phase 5 to verify everything works
3. Move to Phase 3 once Phase 2 is stable
4. Phase 4 is polish (can be done anytime)
5. Phase 6 is optional for future enhancements

---

## References

- [Electron Documentation](https://www.electronjs.org/docs)
- [Electron Builder](https://www.electron.build/)
- [Electron Security Best Practices](https://www.electronjs.org/docs/tutorial/security)
- [Node.js Child Process](https://nodejs.org/api/child_process.html)
