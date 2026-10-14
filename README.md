# True Seasons (Minecraft Bedrock add-on)

Spring, Summer, Fall and Winter for Minecraft Bedrock Edition. Everything the seasons change goes back to normal on its own.

## Install
1. Download **[`dist/TrueSeasons.mcaddon`](dist/TrueSeasons.mcaddon)** and open it. Minecraft imports both packs.
2. In your world's settings, under **Behavior Packs**, activate **True Seasons**. The resource pack is added automatically.
3. Requires Minecraft Bedrock **1.21.130 or newer**. No experimental toggles needed.

Every player gets a **Season Almanac** the first time they join. It can also be crafted from a book and a clock.
Use it to see the season. Operators get a **Settings** button there too.

## The seasons
All four seasons are always the same length. Set the length with the **slider** in Settings (1 to 100 MC days, default 10), or with `/seasons:seasonlength <days>`.

| Season | What happens |
|---|---|
| **Spring** | Days get longer. Heavy rain and misty mornings. Cooler biomes start snowy and thaw as it warms up, with mountains and lakes thawing last. Dead grass and flowers grow back exactly as they were. |
| **Summer** | Long days and short nights. A mix of rain and sun, afternoon thunderstorms, and fireflies on warm nights. Crops out in the sun grow faster. |
| **Fall** | Days get shorter. Misty mornings, falling leaves, and first frosts at night, then grass and flowers die off. |
| **Winter** | Short days and long nights. Snow anywhere that could plausibly get it, from light flurries to the occasional blizzard (white-out fog, sideways snow, fast pile-up). Snow piles up (3 layers by default, up to 8) and lakes freeze over. Deserts, savannas and badlands stay dry unless you're above Y=160. |

### Like real life
- **Day length changes with the season.** At midsummer the day lasts about 13 minutes and the night about 7; midwinter is the reverse. A full day is still 20 minutes. Toggle it in Settings.
- **Temperature is never fixed.** It's warmest mid-afternoon and coldest before dawn, and random warm spells and cold snaps last a few days. Snow can melt during a mild spell in winter, and the first frost can come early in fall.
- **Water lags behind the air.** Lakes and rivers freeze over in midwinter and stay frozen into early spring, after the snow has started melting.
- **Higher means colder.** Mountains get snow earlier and keep it longer.
- **Thermometer:** hold the Season Almanac to see the season and the temperature where you stand (°C, or °F in Settings).

## Why nothing breaks
- **Seasonal snow and ice are the add-on's own blocks.** They look and work like the real ones, and they're the only snow and ice the add-on ever melts. Naturally generated snow, snow players place, and snowy biomes are never touched.
- **Frozen water turns back into the exact water source it was**, whether it thaws or gets mined. Water near farmland never freezes, so farmland can't dry out.
- **A dead plant remembers what it was.** Each type of grass or flower has its own withered block. It grows back into the same plant in spring, and if it's broken while dead, it drops the original plant's items.
- **Snow only settles on natural ground** (grass, dirt, stone, sand, leaves, logs...), never on slabs, chests or farmland. Under trees it settles on the leaves, not the ground beneath.
- **Places nobody visits catch up later.** Bedrock only runs chunks near players, so snow, ice and plants revert as soon as someone comes back.
- **Seasons only move forward.** They follow the day count: sleeping moves them forward, and setting the time back never rewinds a season.

## Commands
| Command | Who |
|---|---|
| `/season` | anyone: shows the current season |
| `/seasons:setseason <spring\|summer\|fall\|winter> [day]` | operators |
| `/seasons:seasonlength <days>` | operators |
| `/seasons:seasonconfig` | operators: opens the settings menu |
| `/seasons:storm <start\|stop>` | operators |
| `/seasons:cleanup [radius]` | operators: removes **all** seasonal snow and ice and brings every dead plant back, within 128 blocks (or the radius you give, up to 256) of every player |

For testing: `/scriptevent seasons:simulate <x> <z> <radius> <columns>` fast-forwards the seasonal block changes around a spot, and `/scriptevent seasons:probe <x> <z>` shows what the add-on sees there.

## Cleaning up snow
- **Melting is fast.** Snow disappears in one go once it's warm enough, not layer by layer. A background sweep checks every column within 64 blocks of each player about every 9 seconds, so thawed snow, ice and dead plants near you clear within seconds.
- **Clean up everything now:** operators can use the **"Clean up all seasonal snow now"** button in the Season Almanac, or `/seasons:cleanup`. It removes all seasonal snow and ice and revives all plants around every player, whatever the season, in a few seconds.
- **No snow at all:** turn off **"Snow settles and piles up"** in Settings. It still snows, but nothing stays, and any seasonal snow left anywhere is removed as players come near it.
- Clean-up only ever touches the add-on's own snow, ice and dead plants. Natural snow and snow players place are never removed.

## Bedrock limits
- Bedrock decides rain vs. snow by biome, so winter snowstorms in warm biomes are drawn by the add-on: snowflake particles and fog instead of vanilla rain. Mist and fireflies are particles and fog too.
- Bedrock can't change grass or leaf colors while the game is running, so autumn shows as falling leaves instead of recolored trees.
- Add-ons can't slow vanilla crop growth, so winter doesn't stop crops. Summer speeds them up.

## Development
`packs/TrueSeasons_BP` contains the behavior pack (scripts in `scripts/`). `packs/TrueSeasons_RP` contains the resource pack.
`python3 tools/build.py` regenerates the block, model, particle and manifest files and rebuilds `dist/TrueSeasons.mcaddon`.
