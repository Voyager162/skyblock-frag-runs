# Validation — version 1.4.0

Checked on Windows with Minecraft 26.2, Java 25, Fabric Loader 0.19.5, and Fabric API 0.161.0+26.2. Build command: `gradle test build --no-daemon` using the pinned Gradle 9.5.1 and Loom 1.17.17.

## Automated checks

**116 tests passed, zero failures, zero errors.** The full report is `build/reports/tests/test/index.html`.

| Area | Tests | Coverage |
| --- | ---: | --- |
| Session | 22 | Entry/exit timing, active and paused clocks, pause/resume inside one run, double presses from either state, the inclusive 1.5-second boundary, key repeats, run statistics, partial stops, and recovery |
| Accounting | 20 | Pickup/inventory reconciliation, expiring credits, exact pre-run and post-run purse balances, transaction exclusions, interrupted or late returns, coin losses, pricing, salvage, and seven-category profit breakdown |
| Giant drops | 3 | Carried stacks reappearing across a warp or moving during a kill, matching-encounter evidence, and delayed packet/inventory delivery in both orders |
| Hunting Box | 4 | Product aliases, personal reward messages, old balances, manual deposits, message/balance reconciliation, pause boundaries and Bazaar valuation under Other items |
| Hourly projection | 11 | Average run duration across different speeds, hub downtime, finished share, fixed drop odds, original-mode equivalence, other loot per session hour, zero/live runs, repricing, tax, and restored sessions |
| Dungeon presence | 5 | Pre-warp multiline banner, real transfer, rebuilt or stale hub sidebar, confirmation after arrival, requeue inside F7, Master Mode, expired or cancelled entry, and disconnect |
| Essence | 5 | Hub-balance parsing and reconciliation, chat reward subtraction, salvage receipt subtraction, partial balances, invalidation |
| Layout | 9 | Seven viewport sizes from 320×240 through 3440×1440, multiple scales and anchors, invalid settings, clamping, coordinate round trips |
| HUD sections | 3 | All 512 visibility selections in each fragment mode, smaller bounds at constrained resolutions, compact resizing, and a fully hidden HUD |
| Fragment display | 1 | The combined subtotal matches the separate rows across price updates, taxed sales, NPC fallback, and an unpriced fragment |
| Signals | 15 | Formatted scoreboard/chat parsing, exact F7 recognition, other-floor/Master Mode rejection, purse precision, essence messages, transaction exclusions |
| Data | 18 | Bazaar orders, catalog and salvage, settings and HUD migration, display preferences surviving reload, save recovery, history clearing, and pause persistence |

The seven profit categories use the same valuation as the total. The accounting checks confirm that four giant-fragment subtotals plus all essence, kill coins, and other items equal that total. Bonzo/Livid/dragon fragments and Spirit Leaps are grouped under other items. Found essence and estimated salvage are grouped together; disabling salvage changes gear valuation without adding both values.

## Modpack and rendering checks

### Version 1.4.0

The installable JAR ran with 68 SkyBlock Enhanced mod JARs in a separate Minecraft 26.2 instance. A private fixture supplied a sample session, opened Settings, clicked each HUD control, and captured native game screenshots.

- Confirmed all nine sections initially visible. The combined mode replaced four profit rows with one in both the HUD and the dashboard. The itemized loot list still showed the individual drops.
- Confirmed the two-row profit summary, total alone, hourly alone, breakdown alone, and the all-hidden editor state. The smaller editor accepted drags inside its updated bounds and resized from its new bottom corner.
- Reloaded the saved options from disk and verified the selection, grouping, position and scale. The session snapshot matched its original runs, clock and loot after all display changes.

### Previous version 1.3.0 checks

A live 1.2.0 session recorded two Diamante encounters and credited two Handles. The player confirmed one actual drop. The recorded instant-sell quote was 366,667 coins, matching the observed sale of a single Handle. With 1.25% tax the correct value for one was 362,083.66 coins. Two incorrectly credited units explained the displayed 724.2k.

The final JAR ran in Minecraft 26.2 with the 68 copied SkyBlock Enhanced mods and an isolated integrated server. The replay sent server inventory, chat, menu, entity and transfer packets and delivered press/repeat/release events through Minecraft's keyboard handler.

- The first Diamante died and awarded a Handle. The second died without awarding one. The old Handle disappeared during transfer, reappeared, and moved to a different slot after the second kill. The session recorded two encounters, one Handle and a 50% drop rate. A separate direct inventory award during Watcher dialogue also counted when its giant name label was hidden.
- A server Hunting Box screen showed ten existing Zombie Soldier shards. Two personal captures, two manually deposited shards, and one additional balance gain raised the box to fifteen. The loot ledger credited three and valued them entirely under Other items. A capture received while paused earned zero.
- Holding F7 paused once. Both clocks froze and resumed in the same dungeon. Two distinct presses between client ticks ended the running session, and a double press also ended a previously paused session.
- With a 366,667-coin bid and 1.25% tax, one Handle contributed 362,083.66 coins. Three shards at a 1,000-coin bid contributed 2,962.50. The running client also downloaded a current public Bazaar snapshot.

### Previous version 1.2.0 checks

A user running 1.0.0 on Hypixel recorded an 88-second session with zero runs. The game log showed the Floor VII queue banner, a server transfer about four seconds later, Mort and Watcher dialogue, and a return warp about 62 seconds after entry. The previous detector required a matching sidebar title and row before tracking any run activity.

The regression tests use the banner format with a substitute player name. An additional test uses 60-second and 180-second runs to verify a two-minute average and 30 projected attempts per hour, including after an hour of hub downtime.

The packaged 1.2.0 JAR ran with all 68 copied SkyBlock Enhanced mod JARs and a separate test-only mod in an offline Minecraft 26.2 world. The local integrated server sent game packets to the client and changed dimensions to exercise the actual world-transfer, chat, entity and inventory hooks. The test mod supplied the server events and assertions.

- The banner left the clock at zero in the hub. The transfer started the run, which continued through an incomplete sidebar. A named giant spawned and its death registered before the fragment arrived.
- Server inventory updates delivered one L.A.S.R.'s Eye, one Zombie Commander Helmet worth 15 salvage essence, and two Spirit Leaps. A direct Wither Essence reward counted once. Returning to the hub added exactly 80,000 coins from exact purse balances, finalized one completed run and returned the run clock to zero.
- A second F7 entry used split team prefix/suffix text and a decorative sidebar title. Switching that row to M7 closed the F7 attempt as partial.
- The Settings button opened the confirmation dialog. Cancel kept the saved session. Clear removed its displayed statistics, archives and backups. Automated storage tests covered an active run, a reload and a deletion failure.

### Previous version 1.1.0 checks

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

**A live Hypixel F7 playtest of 1.4.0 is pending.** The HUD checks used a local Minecraft world and a sample session. The previous tracking replay covered the packet and display formats exercised.

For that playtest:

1. Start with F7 outside the dungeon. Enter F7, finish a giant, collect its fragment if one drops, then leave. Confirm the current timer returns to zero, the last time matches entry-to-exit, and the giant is counted once.
2. Exit another run before killing its giant. The total/time records should include it; giant shares and drop probabilities should exclude it.
3. Compare collected item quantities and purse changes against the ledger. Inventory rearrangement, selling an item, and salvaging counted gear must not add the same loot again.
4. With exact essence balances visible in the Dungeon Hub tab list, compare before/after balances. Without those balances, only directly observed essence rewards can be counted reliably.
5. Check both zero-fragment and fragment-drop runs, a disconnect, and a manual stop inside F7. Interruptions should remain partial.
6. Switch hourly modes. Check expected giant fragments against average run duration and finished share. Check collected mode against total collected value divided by session hours.

Profit is an estimate: purse deltas can include unrecognized transfers, server-only/sack pickups may bypass observed inventory events, and unknown items have no invented price. Auction premiums, consumed supplies, and order-book depth are outside the estimate. The HUD intentionally omits price-cache status messages.

## Release artifact

`build/libs/skyblock-frag-runs-1.4.0.jar` — 199,542 bytes.

SHA-256: `55914617daf1cd791868fdeadb9563e8c974d6876b0b40854748e69bbe0d99ab`

Install this jar, not the `-sources.jar`. Source code and build files are included in the project workspace.
