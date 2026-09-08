# HeartBeatz - Social Music Player Application

## Overview
HeartBeatz is a modern Android music player application that combines local music playback with social listening features. It allows users to play their local music collection while also offering innovative party/social listening capabilities through synchronized playback and easy sharing mechanisms.

## Key Features

### Core Music Player Functionality
- **Local Music Playback**: Plays audio files stored on the device
- **Smart Library Organization**: Automatically categorizes music by:
  - Songs
  - Albums
  - Artists
  - Genres
  - Folders
  - Playlists
- **Background Playback**: Continues playing when app is not in foreground
- **Audio Visualization**: Includes visualizer capabilities
- **Modern UI**: Material Design with edge-to-edge display

### Social/Listening Features
- **Party Mode**: Synchronized listening experience with friends
- **Easy Joining**: QR/Barcode scanner for quick party access
- **Manual Join Option**: Manual entry for joining parties
- **Cross-device Synchronization**: Share playback state across devices

### Technical Features
- **Comprehensive Permission Handling**: Manages storage, media, microphone, location, and nearby device permissions
- **Theme Support**: Light/Dark/System theme selection
- **Robust Architecture**: MVVM pattern with Repository pattern
- **Jetpack Components**: ViewModel, LiveData, Lifecycle components
- **Modular Design**: Separate library modules for maintainability

## Project Structure

```
HeartBeatz/
├── app/                          # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/giga/tech1000/heartbeatz/
│   │   │   │   ├── MainActivity.java           # Main entry point
│   │   │   │   ├── CustomScannerActivity.java  # QR/Barcode scanner for party joining
│   │   │   │   ├── app_worker/
│   │   │   │   │   └── HeartBeatzApp.java      # Application class
│   │   │   │   ├── architecture/repositories/  # Data repository implementations
│   │   │   │   ├── layouts/                    # UI layouts and adapters
│   │   │   │   ├── layouts/holders/            # View holders for RecyclerView
│   │   │   │   ├── layouts/models/             # Data models for UI
│   │   │   │   ├── interfaces/                 # Interface definitions
│   │   │   │   ├── view_models/                # ViewModel implementations
│   │   │   │   └── observers/                  # Data observers
│   │   │   └── res/                            # Resources (layouts, values, etc.)
│   └── build.gradle                          # App-level Gradle configuration
├── lib/                                      # Library modules
│   ├── Icons_Pack/                           # Icon resources
│   ├── Media_Player/                         # Core media playback functionality
│   ├── Visualizer_Android/                   # Audio visualization
│   ├── Party_Mode/                           # Party/social features
│   ├── Utils/                                # Utility classes
│   ├── Extensions/                           # Additional functionality
│   ├── multiSlidingUpPanel/                  # UI component library
│   └── readableBottomBar/                    # UI component library
├── gradle/                                   # Gradle wrapper
├── build.gradle                              # Project-level Gradle configuration
├── gradle.properties                         # Gradle properties
├── settings.gradle                           # Project settings
└── local.properties                          # Local SDK configuration
```

## Key Components Explained

### MainActivity.java
The main entry point of the application that handles:
- Splash screen initialization and management
- Permission requests and handling
- Core component initialization (ViewModels, observers)
- UI setup with edge-to-edge display
- Party mode state observation and UI updates
- Intent handling for external triggers

Key methods:
- `onCreate()`: Initializes splash screen, permissions, core components
- `setupPermissions()`: Configures permission handling with callbacks
- `checkAndRequestPermissions()`: Checks and requests required permissions
- `loadAudioFiles()`: Initiates media scanning and loading
- `setDataReady()`: Signals when data loading is complete
- `handleIntent()`: Processes incoming intents (e.g., party invites)

### HeartBeatzApp.java
The Application class that:
- Initializes the SettingRepository
- Applies the saved theme preference (Light/Dark/System) on app startup
- Provides static access to settings repository and snapshot

### CustomScannerActivity.java
A specialized activity for scanning QR/barcodes to join party sessions:
- Uses JourneyApps barcode scanner library
- Provides manual join button as alternative
- Handles activity lifecycle for the scanner

### Architecture Overview
The app follows a modern Android architecture:
- **MVVM Pattern**: Separates concerns between UI, business logic, and data
- **Repository Pattern**: Abstracts data sources (local database, network, etc.)
- **ViewModels**: Manage UI-related data lifecycle and survive configuration changes
- **LiveData**: Observable data holders that update UI automatically
- **Dependency Injection**: Uses ViewModelProvider for ViewModel instantiation

### Key Libraries Used
- **AndroidX**: AppCompat, Material, Activity, ConstraintLayout, Navigation, Lifecycle
- **Glide**: Image loading
- **Media3**: Media playback functionality
- **OkHttp**: Networking
- **GSON**: JSON parsing
- **Guava**: Utility libraries
- **ZXing**: Barcode/QR code scanning
- **Palette**: Color extraction from images
- **WorkManager**: Background task scheduling
- **Palette**: Extracting prominent colors from images

## Permissions Used
The app requests comprehensive permissions to enable its full feature set:
- `INTERNET`: Network access
- `CAMERA`: For QR/barcode scanning
- `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE`: Access to media files
- `READ_MEDIA_AUDIO`: Access to audio media (Android 13+)
- `FOREGROUND_SERVICE`: For background audio playback
- `RECORD_AUDIO`: For visualization/features requiring microphone
- `POST_NOTIFICATIONS`: For media playback notifications
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Specifies media playback foreground service
- `ACCESS_NETWORK_STATE`/`ACCESS_WIFI_STATE`: Network state monitoring
- `CHANGE_WIFI_STATE`/`CHANGE_WIFI_MULTICAST_STATE`: WiFi control for sharing
- `ACCESS_COARSE_LOCATION`/`ACCESS_FINE_LOCATION`: Location for nearby features
- `NEARBY_WIFI_DEVICES`: Direct WiFi peer-to-peer communication
- `WRITE_SETTINGS`: System settings modification (e.g., volume)

## Data Flow
1. App starts → Splash screen shows
2. Permissions checked/requested
3. If granted → Media scanning begins
4. ViewModels initialized and observe data changes
5. LibraryObservers coordinate loading of all media types
6. When data is ready → Splash screen dismissed → Main UI shown
7. User interacts with UI → ViewModels update → LiveData notifies UI → UI updates
8. Party mode features → Sync state observed → UI updates accordingly

## How to Use
1. Launch the app
2. Grant storage permissions when prompted
3. Allow media scanning to complete
4. Browse your music library by songs, albums, artists, etc.
5. For party features:
   - Use the barcode scanner to join a party session
   - Or use the manual join option
   - Host a party to share your current playback

## Development Notes
- Uses Java rather than Kotlin
- Follows Android Jetpack guidelines
- Modular architecture facilitates maintenance
- Proper lifecycle management prevents memory leaks
- Comprehensive error handling for permission scenarios
- Designed for both phone and tablet use

## Future Enhancements Possibilities
1. Online music streaming integration
2. Enhanced social features (chat, reactions)
3. Improved playlist management and sharing
4. Cross-platform support (iOS/web)
5. Advanced audio effects and equalizer
6. Cloud library synchronization