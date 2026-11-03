# Changelog

## 1.7.0 — Giant fragments in the stash

- Count all four giant fragment types sent to the stash with a full inventory. Itemized notifications credit immediately. A generic overflow can be matched later to a named collection or an observed increase in the material stash.
- Update the earning run, its drop statistics and collected profit after a warp, session stop or restart. Observe stash withdrawals alongside inventory updates to avoid double counting.
- Preserve ambiguity when a bulk claim covers several possible runs. A shared receipt contributes to the matching session total. Receipts spanning sessions can be assigned in the run log.

## 1.6.0 — Dungeon transfers, Watcher giants and rendered maps

- Confirm F7 in the destination world before starting a run. Keep the arrival time when confirmed and ignore transient hub or floor rows during transfers.
- Identify generic-name giants from equipment packets and changing armor dye. Record a defeated giant independently of a fragment pickup, including nearby combat removals without a vanilla death animation.
- Draw archived maps from saved room footprints, doors, colors and checkmarks, or the server's map palette and decorations. New runs store map data in the profile's `maps` folder. v1.5 image archives have no room model to redraw.

## 1.5.0 — Session log and dungeon maps

- Browse archived sessions in **F9 → Session log**. Each session has totals, an itemized loot breakdown and a clickable list of runs.
- Inspect each new run's timing, exit, giant and confirmed fragment result alongside its kill coins, giant-fragment value, essence, salvage and other items.
- Save a map image from the active StellaNav or Skyblocker HUD. The server's dungeon map supplies room colors when a HUD capture is unavailable.
- Continue a saved session with its previous elapsed time, runs and loot. Saving it again updates the same log entry.
- Read older archives with the fields they originally recorded. Clearing history now also removes the associated run maps.

## 1.4.0 — Customizable HUD

- Open **F9 → Settings → HUD sections** to choose among nine independent sections. The breakdown, total profit and hourly estimate can each be displayed on their own.
- Choose **One combined total** to sum the collected quantity and net value of all giant fragments in the HUD and the Loot & profit breakdown.
- The HUD, editor outline, drag area and resize handle fit the visible content. Hiding every section hides the gameplay overlay. Existing profiles start with all sections visible and separate fragment rows.

## 1.3.0 — Pause, verified fragment counts, and Hunting Box shards

- Press the session key once to start, pause or resume. Two presses within 1.5 seconds end a running or paused session. Paused sessions can resume after a restart.
- Separate Watcher encounter counts from fragment pickup evidence. Carried stacks, late inventory restores and duplicate pickup observations no longer increase the fragment count on a second run.
- Show collected quantity beside each giant fragment subtotal, with the current per-unit Bazaar quote on hover in Loot & profit.
- Include Hunting Box shards in Other items using personal rewards and observed balance increases. Existing stock and manual deposits are excluded.

## 1.2.0 — F7 entry, session history, and run pace

- Recognize the server's Floor VII announcement before chat filters, then start timing at the transfer into the dungeon. Read team-formatted sidebar rows and tab floor markers, and keep a confirmed run through incomplete sidebar updates.
- Track modern giant name labels and reconcile exact purse balances across a run when the dungeon hides the purse.
- Clear saved history from Settings or Run history with confirmation. An active session keeps running.
- Project giant-fragment coins/hour from expected value per encounter, average recorded F7 run time, and the share of runs that finish a giant. Collected mode uses actual session profit and time.

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
