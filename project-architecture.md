# SmartTube – Detailed Architecture

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Repository Structure](#2-repository-structure)
3. [Gradle Modules](#3-gradle-modules)
4. [Build Variants / Flavors](#4-build-variants--flavors)
5. [Architecture Pattern – MVP](#5-architecture-pattern--mvp)
6. [Layer Breakdown](#6-layer-breakdown)
   - 6.1 [View Layer (smarttubetv)](#61-view-layer-smarttubetv)
   - 6.2 [Presenter Layer (common)](#62-presenter-layer-common)
   - 6.3 [Model Layer (common)](#63-model-layer-common)
7. [Navigation – ViewManager](#7-navigation--viewmanager)
8. [Playback Subsystem](#8-playback-subsystem)
9. [Media Service Layer (MediaServiceCore)](#9-media-service-layer-mediaservicecore)
10. [ExoPlayer Integration](#10-exoplayer-integration)
11. [Preferences / Settings](#11-preferences--settings)
12. [Miscellaneous Services](#12-miscellaneous-services)
13. [Third-Party Integrations](#13-third-party-integrations)
14. [Dependency Graph (simplified)](#14-dependency-graph-simplified)

---

## 1. Project Overview

**SmartTube** is a free, open-source Android TV media client for YouTube content. It is optimized for D-pad / remote-controlled TV environments and runs on Android 4.3+. The app is built as a single APK with several product flavors targeting stable, beta, and F-Droid distribution channels.

| Property | Value |
|---|---|
| Language | Java (primary), Kotlin (minor) |
| Minimum SDK | API 18 (Android 4.3) |
| Architecture | MVP (Model-View-Presenter) |
| Reactive layer | RxJava 2 |
| Player engine | ExoPlayer 2.10.6 (Amazon fork) |
| Build system | Gradle 7.4.2 |

---

## 2. Repository Structure

```
SmartTube/
├── smarttubetv/              # Main app module – Android TV UI
├── common/                   # Shared business logic & presenter layer
├── exoplayer-amzn-2.10.6/   # Amazon-modified ExoPlayer (git submodule)
├── MediaServiceCore/         # YouTube API client – git submodule
├── SharedModules/            # Shared utility helpers – git submodule
├── chatkit/                  # Chat UI library
├── leanback-1.0.0/           # Pinned Leanback support library
├── fragment-1.1.0/           # Pinned Fragment support library
├── leanbackassistant/        # Voice assistant bridge
├── doubletapplayerview/      # Double-tap gesture view
├── filepicker-lib/           # File picker UI
└── slidableactivity/         # Slide-to-dismiss activity
```

---

## 3. Gradle Modules

| Module | Role |
|---|---|
| `:smarttubetv` | Application module. Contains all Activities, Fragments, and TV-specific UI code. |
| `:common` | Library module. Houses the entire MVP presenter layer, domain models, ExoPlayer wrappers, preferences, and misc services. |
| `:exoplayer-*` | Amazon-patched ExoPlayer 2.10.6 (DASH, HLS, SmoothStreaming, SABR). |
| `MediaServiceCore` | YouTube data-access interfaces (`ContentService`, `MediaItemService`, `SignInService`, etc.) and their implementations via the YouTube InnerTube API. |
| `SharedModules` | Cross-project utilities: logging, helpers, RxJava utilities, shared preferences primitives. |
| `:chatkit` | Standalone library for rendering live-chat messages inside the player. |
| `:leanback-1.0.0` | Pinned version of `androidx.leanback` for stability on older Android TV firmware. |
| `:fragment-1.1.0` | Pinned `androidx.fragment` to match Leanback requirements. |
| `:leanbackassistant` | Provides the voice-search bridge activity recognized by the Android TV voice system. |
| `:doubletapplayerview` | Custom View that implements YouTube-style double-tap-to-seek behavior. |
| `:filepicker-lib` | File-system picker used in backup/restore flows. |
| `:slidableactivity` | Adds swipe-to-dismiss to any Activity. |

---

## 4. Build Variants / Flavors

Three product flavors are declared, each changing app metadata, icons, and update URLs:

| Flavor | Description |
|---|---|
| `ststable` | Public stable release distributed via GitHub / download links. |
| `stbeta` | Beta / testing channel. |
| `stfdroid` | F-Droid build. Strips proprietary components and telemetry. |

Combined with `debug` / `release` build types, the typical installed variant is `ststableDebug` (development) or `ststableRelease` (production).

---

## 5. Architecture Pattern – MVP

SmartTube follows a strict **Model-View-Presenter** pattern:

```
┌─────────────────┐     View Interface      ┌──────────────────────┐
│  Activity /     │◄───────────────────────►│     Presenter        │
│  Fragment (TV)  │                          │  (common module)     │
│                 │                          │                      │
│  implements     │                          │  holds WeakRef<View> │
│  XxxView        │                          │  calls service APIs  │
└─────────────────┘                          └──────────┬───────────┘
                                                        │
                                              ┌─────────▼───────────┐
                                              │  MediaServiceCore   │
                                              │  (YouTube API)      │
                                              └─────────────────────┘
```

- **Views** are plain Java interfaces (e.g., `BrowseView`, `PlaybackView`). The concrete implementations live in `:smarttubetv` Activities/Fragments.
- **Presenters** extend `BasePresenter<T>` and hold a `WeakReference<T>` to the view. They are singletons managed by the `ViewManager`.
- **Models** are pure data objects (`Video`, `VideoGroup`, `BrowseSection`, etc.) plus reactive data streams from `MediaServiceCore` via RxJava `Observable`.

---

## 6. Layer Breakdown

### 6.1 View Layer (`smarttubetv`)

All Activities register themselves with `ViewManager` on app start.

| Activity / Fragment | View Interface | Purpose |
|---|---|---|
| `SplashActivity` | `SplashView` | App cold-start, auth check |
| `BrowseActivity` / `BrowseFragment` | `BrowseView` | Home screen, section tabs, video rows |
| `PlaybackActivity` / `PlaybackFragment` | `PlaybackView` | Full-screen video player |
| `SearchTagsActivity` / `SearchTagsFragment` | `SearchView` | Search with tag suggestions |
| `ChannelActivity` | `ChannelView` | Channel page |
| `ChannelUploadsActivity` | `ChannelUploadsView` | Channel uploads list |
| `SignInActivity` | `SignInView` | Google account sign-in |
| `AddDeviceActivity` | `AddDeviceView` | Add device to account |
| `AppDialogActivity` | `AppDialogView` | Generic settings/dialog sheet |
| `WebBrowserActivity` | `WebBrowserView` | Embedded web view |

#### Browse UI (video grids)
Located under `tv/ui/browse/video/`:

| Fragment | Purpose |
|---|---|
| `VideoRowsFragment` | Horizontal rows of videos (standard home layout) |
| `VideoGridFragment` | Single vertical/horizontal grid |
| `MultipleRowsFragment` | Multi-row layout for combined sections |
| `MultiVideoGridFragment` | Multiple video grids |
| `ShortsGridFragment` | YouTube Shorts-specific grid |

#### Playback UI modifications (`tv/ui/playback/mod/`)
- `EventsOverridePlaybackFragment` – intercepts D-pad key events for custom actions.
- `SeekModePlaybackFragment` – overrides seeking behavior.
- `surface/` – render surface management.

### 6.2 Presenter Layer (`common`)

All presenters extend `BasePresenter<T>` which provides:
- Access to `ViewManager` for navigation
- Lazy-resolved service references (`MediaItemService`, `ContentService`, `SignInService`, etc.)
- Helpers for opening Playback, Browse, Channel, Search views

#### Top-level presenters

| Presenter | Responsibility |
|---|---|
| `SplashPresenter` | Boot checks, auto-sign-in, first-run setup |
| `BrowsePresenter` | Loads home feed sections, handles section switching, account changes |
| `PlaybackPresenter` | Orchestrates the playback controller chain (see §8) |
| `SearchPresenter` | Query submission, result grouping |
| `ChannelPresenter` | Fetches and displays a channel's overview |
| `ChannelUploadsPresenter` | Paginates uploads for a given channel |
| `AppDialogPresenter` | Generic dialog/settings sheet host |
| `SignInPresenter` / `YTSignInPresenter` | Google OAuth + YouTube token flow |
| `GoogleSignInPresenter` | Secondary Google account sign-in |
| `AddDevicePresenter` | Link additional device |
| `WebBrowserPresenter` | Web view navigation |

#### Settings presenters (`presenters/settings/`)
One presenter per settings category: `GeneralSettingsPresenter`, `PlayerSettingsPresenter`, `SponsorBlockSettingsPresenter`, `DeArrowSettingsPresenter`, `AutoFrameRateSettingsPresenter`, `BackupSettingsPresenter`, `AccountSettingsPresenter`, `SubtitleSettingsPresenter`, `RemoteControlSettingsPresenter`, `MainUISettingsPresenter`, `UIScaleSettingsPresenter`, `SearchSettingsPresenter`, `LanguageSettingsPresenter`, `AboutSettingsPresenter`.

#### Dialog/menu presenters (`presenters/dialogs/`)

| Presenter | Purpose |
|---|---|
| `VideoMenuPresenter` | Context menu for a video item (add to playlist, open channel, block, etc.) |
| `VideoActionPresenter` | Immediate action on video (play, open, queue) |
| `SectionMenuPresenter` | Context menu for a browse section tab |
| `ChannelUploadsMenuPresenter` | Context menu for channel-uploads entry |
| `AppUpdatePresenter` | In-app update check/prompt |
| `BridgePresenter` / `ATVBridgePresenter` / `AmazonBridgePresenter` | Handles voice-search deep-links from the OS |
| `AccountSelectionPresenter` | Multi-account switcher |
| `BootDialogPresenter` | First-launch onboarding dialogs |

### 6.3 Model Layer (`common`)

#### Data objects (`app/models/data/`)

| Class | Description |
|---|---|
| `Video` | Central domain object. Wraps `MediaItem` + `MediaItemMetadata` + local state (position, playlist info, chapter list). |
| `VideoGroup` | Named list of `Video` objects, corresponds to a `MediaGroup` row. |
| `BrowseSection` | A tab/section in the home screen (id, title, type, associated `VideoGroup`). |
| `Playlist` | Ordered list of `Video` objects, tracks current index. |
| `SettingsGroup` / `SettingsItem` | Generic key-value settings model used by dialog presenters. |
| `SimpleMediaItem` | Lightweight `MediaItem` wrapper for URLs or offline content. |

#### Playback models (`app/models/playback/`)
See §8 for details.

---

## 7. Navigation – ViewManager

`ViewManager` is the application's navigation bus. It is a singleton initialized in `MainApplication.onCreate()`.

```java
// Registration (MainApplication)
viewManager.setRoot(BrowseActivity.class);
viewManager.register(PlaybackView.class,      PlaybackActivity.class,      BrowseActivity.class);
viewManager.register(SearchView.class,        SearchTagsActivity.class,    BrowseActivity.class);
viewManager.register(ChannelView.class,       ChannelActivity.class,       BrowseActivity.class);
// ... etc.
```

**How navigation works:**
1. A presenter calls `ViewManager.startView(XxxView.class)`.
2. `ViewManager` looks up the registered `Activity` class for that view interface.
3. It starts the `Activity` with an `Intent`, optionally tracking the parent `Activity` for back-stack management.
4. An internal `Stack<Class<Activity>>` maintains the activity history so the back button works correctly.

This decouples presenters entirely from Android `Activity` references – they only reference view interfaces.

---

## 8. Playback Subsystem

The playback subsystem is the most complex part of the application. It uses a **chain-of-responsibility** pattern: `PlaybackPresenter` holds a list of `PlayerEventListener` controllers, each responsible for a specific concern.

```
PlaybackPresenter (implements PlayerEventListener)
│
├── VideoStateController     – saves/restores playback position, shuffle state
├── SuggestionsController    – loads "up next" video suggestions
├── VideoLoaderController    – resolves format URLs via MediaServiceCore, starts ExoPlayer
├── PlayerUIController       – manages player overlay UI (title, buttons, progress bar)
├── HQDialogController       – quality/format selection dialog
├── SponsorBlockController   – polls SponsorBlock API, triggers timed segment skips
├── AutoFrameRateController  – communicates with DisplayManager for AFR switching
├── RemoteController         – handles remote-control deep-links and cast commands
├── ChatController           – live-chat WebSocket streaming via chatkit
└── CommentsController       – YouTube comments loading
```

Every player event (play, pause, seek, track change, buffering, error, etc.) is dispatched to all registered `PlayerEventListener` implementations in order.

### Key interfaces

| Interface | Location | Description |
|---|---|---|
| `PlayerManager` | `app/models/playback/manager/` | Full player API (extends `PlayerEngine` + `PlayerUI`) |
| `PlayerEngine` | same | Low-level transport controls (play, pause, seek, tracks) |
| `PlayerUI` | same | UI overlay controls (show/hide title, subtitles, suggestions) |
| `PlayerEventListener` | `app/models/playback/listener/` | Callback interface for all player events |
| `BasePlayerController` | `app/models/playback/` | Abstract base for all controllers; holds ref to `PlaybackPresenter` |

### ExoPlayer binding

```
PlaybackFragment
    └── ExoPlayerController (implements Player.EventListener)
            ├── SimpleExoPlayer (exoplayer-amzn)
            ├── ExoMediaSourceFactory  → builds DASH/HLS MediaSource
            ├── TrackSelectorManager   → audio/video/subtitle track selection
            └── TrackErrorFixer        → retries on codec errors
```

`ExoPlayerController` is the concrete `PlayerEngine` implementation. It wraps `SimpleExoPlayer` and translates high-level `FormatItem` selections into ExoPlayer `TrackSelector` parameters.

---

## 9. Media Service Layer (MediaServiceCore)

`MediaServiceCore` is a git submodule that provides a clean interface to the YouTube InnerTube API. The `:common` module never calls YouTube directly – it talks to these interfaces:

| Interface | Responsibility |
|---|---|
| `ServiceManager` | Entry point; provides all other services |
| `ContentService` | Browse home feed, subscriptions, trending, playlists |
| `MediaItemService` | Video metadata, format info (`MediaItemFormatInfo`), SponsorBlock segments, dislike data |
| `SignInService` | OAuth2 sign-in, account switching, token management |
| `NotificationsService` | Notification bell state, notification list |
| `CommentsService` | Thread listing and replies |

`MediaServiceManager` (in `:common/misc/`) is a thin singleton that obtains `YouTubeServiceManager` and caches the service instances, adding account-change broadcast logic on top.

---

## 10. ExoPlayer Integration

SmartTube uses **Amazon's fork of ExoPlayer 2.10.6** (`exoplayer-amzn-2.10.6`), which adds:
- **SABR** (Streaming Adaptive Bitrate) extension (`library/sabr/`)
- Various Fire TV / Fire Stick buffer fixes

Key ExoPlayer customizations in `:common`:

| Class | Purpose |
|---|---|
| `ExoMediaSourceFactory` | Builds `DashMediaSource` / `HlsMediaSource` / `ProgressiveMediaSource` from YouTube format URLs |
| `TrackSelectorManager` | Wraps `DefaultTrackSelector`; applies user-selected `FormatItem` constraints |
| `FormatItem` / `ExoFormatItem` | Represent a concrete audio/video/subtitle format choice |
| `TrackSelectorUtil` | Utility for converting `MediaFormat` → ExoPlayer track selection |
| `TrackErrorFixer` | Catches codec-init errors and retries with a fallback format |
| `VolumeBooster` | Amplifies audio gain above 100% using `AudioProcessor` |
| `LiveDashManifestParser` | Custom parser for YouTube's live DASH manifests |

---

## 11. Preferences / Settings

All preferences are stored in Android `SharedPreferences`. Each settings domain has a dedicated data class in `common/prefs/`:

| Prefs class | Domain |
|---|---|
| `PlayerData` | Video quality profile, speed, aspect, subtitles, volume |
| `PlayerTweaksData` | Low-level tweaks: buffer size, codec flags, decoder priority |
| `GeneralData` | App-wide settings: boot behavior, screensaver, back button |
| `MainUIData` | UI scale, card sizes, home screen layout |
| `SponsorBlockData` | Enabled categories, skip actions |
| `DeArrowData` | DeArrow title/thumbnail replacement settings |
| `AccountsData` | Active account selection, account list |
| `SearchData` | Search language, safe-search |
| `RemoteControlData` | Remote-control server port, paired devices |
| `BlockedChannelData` | Channel block list |
| `AppPrefs` | Miscellaneous flags (first-run, crash recovery) |
| `HiddenPrefs` | Developer / debug flags |

---

## 12. Miscellaneous Services

Located in `common/misc/`:

| Class | Purpose |
|---|---|
| `MediaServiceManager` | Singleton mediator for all `MediaServiceCore` service instances; broadcasts account changes |
| `AppDataSourceManager` | Provides data sources for browse sections (maps section type → API call) |
| `BrowseProcessorManager` | Pipeline of `BrowseProcessor` instances post-processing video groups (e.g., filtering blocked channels) |
| `DeArrowProcessor` | Replaces video titles/thumbnails with DeArrow community suggestions |
| `UnlocalizedTitleProcessor` | Strips localization suffixes from titles |
| `RemoteControlService` | Background `Service` exposing HTTP endpoint for remote-control integrations |
| `BackgroundPlaybackService` | `Service` keeping ExoPlayer alive when the app is backgrounded |
| `BackupAndRestoreManager` | Serializes/deserializes all `SharedPreferences` to JSON for backup |
| `GDriveBackupManager` | Google Drive backup worker using `WorkManager` |
| `ScreensaverManager` | Controls Android TV screensaver activation |
| `TickleManager` | Periodic heartbeat used to refresh token and keep sessions alive |
| `CrashRestorer` | On crash, saves a restore point; re-applies it on next launch |
| `GlobalKeyTranslator` / `PlayerKeyTranslator` | Remaps hardware key codes to app actions |
| `ProxyManager` | HTTP/SOCKS proxy configuration for network requests |

---

## 13. Third-Party Integrations

| Integration | How it works |
|---|---|
| **SponsorBlock** | `SponsorBlockController` calls the SponsorBlock open API with the video ID (privacy-preserving partial hash). Timed `Observable` polls every second and triggers seeks over marked segments. |
| **DeArrow** | `DeArrowProcessor` (a `BrowseProcessor`) replaces `VideoGroup` titles and thumbnails with community submissions fetched by video ID. |
| **Return YouTube Dislike (RYD)** | Video dislike count is fetched from the RYD API and injected into the `Video` model's metadata. |
| **Google Drive Backup** | `GDriveBackupWorker` (a `WorkManager` worker) serializes preferences to JSON and uploads to the user's Google Drive app folder. |
| **Remote Control** | `RemoteControlService` starts a local HTTP server; a companion mobile app sends playback commands over LAN. |
| **Voice Search Bridge** | `ATVBridgePresenter` / `AmazonBridgePresenter` receive `Intent`s from the OS voice-search system, parse the query, and dispatch to `SearchPresenter`. |

---

## 14. Dependency Graph (simplified)

```
:smarttubetv  (app)
    │
    ├── :common
    │       ├── MediaServiceCore  (submodule)
    │       │       └── YouTube InnerTube API
    │       ├── SharedModules     (submodule)
    │       ├── :exoplayer-library-core
    │       ├── :exoplayer-library-dash
    │       ├── :exoplayer-library-hls
    │       ├── :exoplayer-library-smoothstreaming
    │       └── RxJava 2
    │
    ├── :leanback-1.0.0
    ├── :fragment-1.1.0
    ├── :chatkit
    ├── :leanbackassistant
    ├── :doubletapplayerview
    ├── :filepicker-lib
    └── :slidableactivity
```
