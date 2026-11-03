# Validation — version 1.1.0

Checked on Windows with Minecraft 26.2, Java 25, Fabric Loader 0.19.5, and Fabric API 0.161.0+26.2. Build command: `gradle test build --no-daemon` using the pinned Gradle 9.5.1 and Loom 1.17.17.

## Automated checks

**90 tests passed, zero failures, zero errors.** The full report is `build/reports/tests/test/index.html`.

| Area | Tests | Coverage |
| --- | ---: | --- |
| Session | 19 | Entry/exit timing, downtime, fastest/slowest/last/average, early exits, repeated giant observations, drop probabilities, stop confirmation/expiry, rapid separate presses, held-key/duplicate-delivery protection, partial stops, crash checkpoints, stopped-session recovery, reset, hourly profit |
| Accounting | 18 | Pickup/inventory reconciliation in either order, coalesced pickups, expiring credits, purse baselines, excluded transactions, coin losses, repricing, tax, NPC fallbacks, unknown prices, salvage without double counting, quality lots, invalid values, save round trips, seven-category profit breakdown |
| Hourly projection | 11 | Fixed probability model, independence from giant/drop luck, original-mode equivalence, non-giant accounting, early exits, zero/live runs, repricing, tax/NPC fallback, invalid quotes, unchanged ledger/statistics, stopped/recovered sessions |
| Essence | 5 | Hub-balance parsing and reconciliation, chat reward subtraction, salvage receipt subtraction, partial balances, invalidation |
| Layout | 9 | Seven viewport sizes from 320×240 through 3440×1440, multiple scales and anchors, invalid settings, clamping, coordinate round trips |
| Signals | 15 | Formatted scoreboard/chat parsing, exact F7 recognition, other-floor/Master Mode rejection, purse precision, essence messages, transaction exclusions |
| Data | 13 | Public Bazaar order parsing, invalid responses, bundled catalog, item-specific salvage and bonuses, persistence, corrupt-save recovery, new-install defaults, 1.0.0 migration, and saved mode selection |

The seven profit categories use the same valuation as the total. The accounting checks confirm that four giant-fragment subtotals plus all essence, kill coins, and other items equal that total. Bonzo/Livid/dragon fragments and Spirit Leaps are grouped under other items. Found essence and estimated salvage are grouped together; disabling salvage changes gear valuation without adding both values.

## Modpack and rendering checks

### Version 1.1.0

- Started the 1.1.0 jar with all 68 copied SkyBlock Enhanced mod jars in the separate test instance.
- Used an explicitly labeled preview fixture with one elapsed hour, eight finished Bigfoot encounters, no giant-fragment drops, and collected coins/essence/Spirit Leaps.
- The migrated 1.0.0 settings selected Expected drops by default. The HUD showed about 2.45M coins/hour while collected profit stayed about 698.8k at the observed market prices.
- The settings button fit in the existing panel and switched immediately to Collected drops. The overview then showed about 698.8k coins/hour, matching the one-hour collected profit, without changing giant/drop counts. The saved configuration recorded `expectedFragmentProfit: false`.
- Fully restarted Minecraft and confirmed the HUD still used Collected drops. Switching back to Expected drops immediately restored about 2.45M coins/hour while collected profit remained about 698.8k and the observed counts stayed unchanged.
- Both HUD and overview identify which hourly mode is active. The version badge correctly displays 1.1.0 from Fabric metadata.

### Previous version 1.0.0 checks

- Copied the installed SkyBlock Enhanced profile's **68 mod jars** into a separate workspace test instance and added this mod. Fabric reports 205 mods including nested components.
- Successfully started the pack and loaded a local Creative world with the built jar. Client packet mixins loaded without a Frag Runs mixin error.
- Confirmed live public Bazaar retrieval and repricing of sample loot in the running client.
- Inspected the HUD and optional detail/settings screens, including a native-resolution Minecraft screenshot. The development screenshot uses explicitly labeled sample session data.
- Verified HUD dragging, corner resizing, reset, and saved relative coordinates. Verified both pages of the categorized loot view.
- Found MarketGuard's default F8 editor binding during gameplay testing. The release uses **F7** for sessions and **F9** for details/settings; neither overlaps the observed pack bindings.
- In ordinary gameplay with development preview mode disabled, F7 started the HUD without a menu or blur, the session timer continued, current run stayed zero outside F7, and an expired stop prompt left the session running. F9 opened the optional details screen over the world.
- The final release's shared stop control accepted two quick clicks, displayed `SESSION SAVED`, froze the timer, and wrote a 13,551 ms session checkpoint. Closing details returned to gameplay with the stopped HUD hidden. Rapid keyboard press/release and held-key protection are covered by the input-latch tests.
- Verified that the jar declares a client-only environment and the required Minecraft/Fabric/Java versions, includes its mixin and offline catalog, and excludes test data, local configurations, and development tools.

The test instance used an offline preview identity. Other pack mods and Minecraft printed expected authorization/Realms errors for that identity, plus optional integration warnings. Those do not establish a Frag Runs failure. The real Modrinth profiles and account credentials were not modified.

## Limits and live-play checklist

**No authenticated Hypixel F7 dungeon run was performed.** Automated accounting/parser tests and local pack testing cannot verify every current Hypixel packet, name tag, purse format, or tab-list layout. This release should receive a real frag-run playtest before its numbers are relied on.

For that playtest:

1. Start with F7 outside the dungeon. Enter F7, finish a giant, collect its fragment if one drops, then leave. Confirm the current timer returns to zero, the last time matches entry-to-exit, and the giant is counted once.
2. Exit another run before killing its giant. The total/time records should include it; giant shares and drop probabilities should exclude it.
3. Compare collected item quantities and purse changes against the ledger. Inventory rearrangement, selling an item, and salvaging counted gear must not add the same loot again.
4. With exact essence balances visible in the Dungeon Hub tab list, compare before/after balances. Without those balances, only directly observed essence rewards can be counted reliably.
5. Check both zero-fragment and fragment-drop runs, a disconnect, and a manual stop inside F7. Interruptions should remain partial.
6. Switch hourly modes during a session. Expected mode should use fixed probabilities and finished encounters per full session hour; Collected mode should match actual total value divided by session hours. The session's loot ledger and observed drop statistics must stay the same.

Profit is an estimate: purse deltas can include unrecognized transfers, server-only/sack pickups may bypass observed inventory events, and unknown items have no invented price. Auction premiums, consumed supplies, and order-book depth are outside the estimate. The HUD intentionally omits price-cache status messages.

## Release artifact

`build/libs/skyblock-frag-runs-1.1.0.jar` — 155,108 bytes.

SHA-256: `09C7206CF4C6106F37D3295534EB71E5670B616A70EB2DCD55AF5D8D4D4D13ED`

Install this jar, not the `-sources.jar`. Source code and build files are included in the project workspace.
