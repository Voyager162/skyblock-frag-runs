# Changelog

## 1.1.0 — Expected giant-fragment income

- Added **Settings → Giant fragment profit/h: Expected drops / Collected drops**.
- Expected drops is the default for new installs and upgrades from 1.0.0. It uses fixed 25% giant spawn and 50% fragment drop probabilities, current prices, and finished encounters per full session hour.
- Collected drops restores the original hourly calculation. The setting persists across restarts.
- Only the giant-fragment portion of coins/hour changes. Actual session totals, loot, drop rates, essence, kill coins, and other-item accounting are preserved.
- HUD and overview identify the active hourly mode; displayed mod version comes from Fabric metadata.
- Added 14 tests covering probability-based estimates, luck independence, early exits, pricing, tax, salvage, migration, and persistence. All 90 tests pass.

This remains a prerelease pending authenticated Hypixel F7 playtesting.

## 1.0.0 — Initial prerelease

- Client-only Fabric 26.2 mod with an SBA-inspired movable, resizable HUD.
- F7 session start and two-press stop confirmation; F9 details and settings.
- Session and F7 run timers, fastest/slowest/last/average times, and run history.
- Watcher giant encounter shares and fragment drop rates, excluding partial encounters.
- Live profit estimates split by each giant fragment, all essence, kill coins, and other drops.
- Background Bazaar pricing, NPC fallbacks, estimated salvage, and hourly profit.
- Local session checkpoints, archives, backup recovery, and optional Mod Menu integration.
- 76 passing automated tests and local SkyBlock Enhanced startup/HUD checks.

Authenticated Hypixel F7 tracking still requires a live playtest. Profit estimates depend on observable game data; see VALIDATION.md and README.md.
