# Skyblock Frag Runs

A client-only **Fabric 26.2** mod for SkyBlock Enhanced. Starting a session displays a movable HUD over gameplay. It does not open a screen or capture the mouse.

**Prerelease 1.2.0** fixes a missed F7 entry reported with SkyBlock Enhanced. Live verification of the fix on Hypixel is pending. [Validation details](VALIDATION.md)

[Download v1.2.0](https://github.com/Voyager162/skyblock-frag-runs/releases/tag/v1.2.0) · [Report an issue](https://github.com/Voyager162/skyblock-frag-runs/issues)

## Install

1. Close the Minecraft instance.
2. Download `skyblock-frag-runs-1.2.0.jar` from the GitHub release and replace the previous Frag Runs JAR in the **mods** folder of your **Minecraft 26.2** SkyBlock Enhanced instance. Keep the profile's configuration folder to retain sessions and settings.
3. Launch the instance. Fabric API is already included in the pack. Do not install the `-sources.jar`.

Targets Minecraft 26.2, Fabric Loader 0.19.3 or newer, Fabric API 0.161.0 or newer, and Java 25. The tested pack uses Loader 0.19.5 and Fabric API 0.161.0+26.2. Mod Menu integration is optional. The mod has no dependency on SkyblockAddons, Skyblocker, SkyHanni, or Odin.

## Controls

| Control | Action |
| --- | --- |
| **F7** | Start a fresh session and show the HUD. |
| **F7, then F7 again within 4 seconds** | Stop and save the current session. The first press only asks for confirmation. |
| **F9** or **`/fragruns`** | Open session details, loot, run history, and settings. |
| **`/fragruns settings`** | Open settings directly. |
| **Mod Menu → Skyblock Frag Runs → Configure** | Open settings. |
| **`/fragruns session`** | Same session toggle and confirmation as F7. |

Rebind the keys in **Options → Controls → Key Binds → Skyblock Frag Runs**. F7 avoids MarketGuard's F8 HUD editor in SkyBlock Enhanced. Closing the settings/details screen keeps the session running. Stopping hides the HUD unless **Show saved HUD** is enabled. Starting a new session archives the previous one.

## HUD and layout

The HUD shows session time, current run time, fastest/slowest/last/average run time, total runs, finished/early runs, each giant's count and share, each fragment's count and drop rate, estimated profit, and estimated coins per hour. Its profit breakdown has a row for each of the four giant fragments, **All essence** (found and estimated salvage), **Kill coins**, and **Other items** (including Spirit Leaps, Bonzo/Livid/dragon fragments, and remaining drops). The same categories appear next to the itemized loot list in the optional details screen.

Open **Settings → Edit in-game HUD layout**. Drag anywhere in the outlined panel to move it. Scroll, use `+`/`-`, or drag its lower-right corner to resize. Arrow keys move it precisely; `R` resets it; `Esc` or `Enter` saves. Positions are relative to the viewport and clamped to the screen, so changing resolution or GUI scale keeps the panel reachable. The separate details screen has its own layout.

The design uses original drawing code with the SkyblockAddons palette: cyan `#A0E1E5`, dark panels `#1B1D29`, Minecraft text, and familiar giant colors. Font appearance follows the active resource pack and font mods.

## What the counters mean

- **Session time:** real elapsed time from starting to stopping. It includes time between runs and disconnects while Minecraft remains open. It does not depend on server TPS.
- **Current run** starts on arrival in the dungeon world after a Floor VII announcement, or when the sidebar or tab list identifies normal F7. It stops when you leave. Starting a session midway through a detected F7 times from activation.
- **Total, fastest, slowest, last, average:** all recorded exits, including early resets. The current run enters these statistics when it ends.
- **Finished giant encounter:** the giant was identified and its death, matching fragment pickup, or Watcher clear was observed before leaving. Merely seeing a giant and leaving early does not count toward giant statistics. Disconnects and manually stopping inside the dungeon are partial runs.
- **Giant share:** that giant's finished encounters divided by all finished giant encounters.
- **Fragment drop rate:** finished encounters with at least one collected matching fragment divided by finished encounters for that giant. Fragment quantity is displayed separately, so a multiple-fragment drop cannot produce a chance above 100%.
- **Profit/hour** uses the selected hourly mode below.

The four fragment IDs are `GIANT_FRAGMENT_DIAMOND`, `GIANT_FRAGMENT_LASER`, `GIANT_FRAGMENT_BIGFOOT`, and `GIANT_FRAGMENT_BOULDER`.

## Hourly profit modes

Open **F9 → Settings → Giant fragment profit/h** to switch modes. The choice is saved immediately and can be changed during a session.

- **Expected drops (default)** projects the value of giant fragments using the average F7 run time on the HUD, your share of finished encounters, and the probabilities below. Essence, kill coins, and other loot contribute their collected value per elapsed session hour.
- **Collected drops:** restores the 1.0.0 calculation: all collected loot value divided by the full session time.

The expected model uses a **25% spawn chance for each giant** and a **50% chance of its one-fragment drop**, giving 0.125 expected units of each fragment per finished encounter. It uses current instant-sell prices after your configured tax, with the same untaxed NPC fallback as collected loot when a quote is missing.

```text
Expected giant-fragment value per run = 0.125 × sum of the four net fragment prices
Expected fragment coins/hour = expected value per finished encounter
  × (finished encounters / recorded runs) × (3,600 / average run seconds)
Expected-mode coins/hour = expected fragment coins/hour
  + collected non-giant profit / elapsed session hours
```

A two-minute average gives 30 attempts per hour. If you finished the giant in eight of ten attempts, that projects 24 eligible encounters per hour. The average includes timed early exits and excludes hub downtime. The fragment projection starts after the first finished encounter with a positive average time. Selectively skipping particular giants can affect realized income.

**Only the hourly estimate changes.** Session profit, the profit breakdown, collected quantities, giant percentages, and observed drop rates remain based on actual observations. Expected income is never added to saved loot. Existing 1.0.0 settings gain the new default automatically; selecting Collected drops persists across restarts.

Probability references: [Watcher giant spawn table](https://hypixelskyblock.minecraft.wiki/w/The_Watcher#Summoned_Mobs), [Diamante's Handle](https://hypixelskyblock.minecraft.wiki/w/Diamante%27s_Handle#Obtaining), [L.A.S.R.'s Eye](https://hypixelskyblock.minecraft.wiki/w/L.A.S.R.%27s_Eye#Obtaining), [Bigfoot's Bola](https://hypixelskyblock.minecraft.wiki/w/Bigfoot%27s_Bola#Obtaining), and [Jolly Pink Rock](https://hypixelskyblock.minecraft.wiki/w/Jolly_Pink_Rock#Obtaining), checked November 2, 2026.

## Profit accounting

The mod listens to incoming game data; it does not perform actions on your behalf.

**Drops:** physical item pickup packets and positive inventory changes are reconciled so the same drop is counted once. Inventory is baselined on entering F7; existing items are excluded. Slot rearrangement is aggregated, container transfers are suppressed, and previously seen unique item UUIDs are excluded. This covers giant/boss/dragon fragments, spirit leaps, superboom, and other recognized drops. Loot remains in the session ledger after you sell it.

**Kill coins** use exact purse changes during F7. If the dungeon hides the purse, a recent exact balance before entry and the first exact hub balance within ten seconds after exit can capture the difference. Recognized transactions cancel the comparison. Rounded balances such as `1.2M` cannot supply exact gains.

**Essence:** direct essence messages are counted. If your Dungeon Hub tab list exposes exact essence balances before and after a run, additional silent essence gains are reconciled after returning to the hub. Already-counted chat rewards and actual salvage receipts are subtracted from that balance change. Essence trading invalidates that reconciliation interval. Without those balances, silent essence gains cannot be recovered reliably.

**Salvage:** known dungeon mob gear is valued as its item-specific essence yield, including the +50% quality bonus and star bonus. API-provided salvage values take precedence. The mod estimates the value when the gear is collected; salvage chat is not added again. Switching off **Include salvage** values those items at their item price instead. Unknown salvage families are not assigned invented yields.

**Prices:** public, keyless Hypixel Bazaar buy orders provide the current instant-sell quote. Prices refresh every 60 seconds on a background thread; the entire ledger is revalued after updates. A configurable sell tax is applied (default 1.25%). No buy orders means no Bazaar quote. Item metadata supplies NPC sell-price fallbacks, and other items remain visibly **Unpriced**. Auction-only items are **not** given speculative auction prices. Quotes are estimates; order-book depth, auction premiums, supplies consumed, and unobserved transactions can change actual realized profit.

A failed refresh retains the last good prices while retrying in the background. Price-cache/status messages are omitted from the HUD. No API key, account token, or Hypixel login is requested by this mod.

## Saves

Open **F9 → Settings → Clear session history** (also available under **Run history**) and confirm **Clear history**. This applies to the current Minecraft profile. During a session it clears older sessions and keeps the active run and loot. After stopping it also clears the displayed last session. Settings and price data are kept.

Files live under the instance's `config/fragruns/` directory:

- `settings.json`: layouts, HUD options, tax, refresh interval.
- `session.json`: checkpoint saved every 10 seconds and on stop/disconnect/shutdown.
- `session.json.bak`: previous checkpoint.
- `history/*.json`: archived sessions.
- `prices.json`: last good price cache.

Writes replace files atomically when supported. Corrupt saves are preserved, with backup recovery attempted. Recovered sessions are stopped: time while the game was closed is never added. A run interrupted by a crash is retained as partial up to the latest checkpoint.

## Build and tests

Install a **JDK 25**, set `JAVA_HOME`, and run:

```powershell
.\gradlew.bat test build
```

Linux/macOS:

```sh
./gradlew test build
```

The wrapper pins Gradle 9.5.1 and the project pins Loom 1.17.17. Test results are in `build/reports/tests/test/index.html`. See `VALIDATION.md` for the checks performed and their limits.

## Data and design references

The initial implementation, tests, and documentation were generated with an AI coding agent from the project owner's feature requirements. This repository makes that source available for inspection and improvement. No AI service is required to run the mod.

- [SkyBlock Enhanced on Modrinth](https://modrinth.com/modpack/skyblock-enhanced)
- [Fabric's Minecraft 26.2 migration notes](https://fabricmc.net/2026/06/15/262.html)
- [Hypixel public API](https://api.hypixel.net/) — public Bazaar orders and item metadata; a reduced item catalog is bundled for offline fallback.
- [Hypixel salvage table](https://wiki.hypixel.net/Salvaging) — base dungeon salvage yields and bonuses.
- [SkyblockAddons](https://github.com/BiscuitDevelopment/SkyblockAddons) — visual reference. No SkyblockAddons code, textures, branding, or fonts are bundled.

This project is not affiliated with Hypixel, SkyBlock Enhanced, SkyblockAddons, Mojang, or Microsoft.
