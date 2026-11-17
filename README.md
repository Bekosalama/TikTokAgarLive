# TikTok Agar Live — Android Prototype

## Included in v0.1
- Landscape Android game screen.
- 500 autonomous AI cells (local simulation).
- Colorful arena, pellets, green virus obstacles.
- Cell growth, AI eating, player-vs-bot eating.
- Top 10 leaderboard and live mass/eaten HUD.
- Optional touch-to-guide override while auto-pilot remains enabled.

## Build
1. Open this folder in Android Studio (Hedgehog or newer).
2. Allow Gradle sync and install Android SDK 35 if prompted.
3. Run the `app` configuration on an Android device or emulator.

## Important prototype boundaries
- This is an offline single-device simulation. The 500 cells are bots, not 500 network-connected viewers.
- TikTok LIVE gift ingestion, viewer identity/profile photo loading, accounts, cloud multiplayer and production server are not connected yet.
- TikTok integration must use an authorized and currently supported LIVE event integration. Do not rely on unofficial scraping or credential sharing.
- The 500-agent target needs profiling on the target device range; this prototype is a functional starting point, not a performance certification.
