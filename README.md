# Dynamic Seasons (Minecraft Bedrock add-on)

Spring, Summer, Fall and Winter for Minecraft Bedrock Edition. Everything the seasons change goes back to normal on its own.

## Install
1. Download **[`dist/DynamicSeasons.mcaddon`](dist/DynamicSeasons.mcaddon)** and open it. Minecraft imports both packs.
2. In your world's settings, under **Behavior Packs**, activate **Dynamic Seasons**. The resource pack is added automatically.
3. Requires Minecraft Bedrock **1.21.130 or newer**. No experimental toggles needed.

Every player gets a **Season Almanac** the first time they join. It can also be crafted from a book and a clock.
Use it to see the season. Operators get a **Settings** button there too.

## The seasons
All four seasons are always the same length. Set the length with the **slider** in Settings (1 to 100 MC days, default 10), or with `/seasons:seasonlength <days>`.

| Season | What happens |
|---|---|
| **Spring** | Heavy rain. Cooler biomes start snowy and switch to rain as it warms up. Snow and ice melt. Dead grass and flowers grow back exactly as they were. |
| **Summer** | A mix of rain and sun, with thunderstorms more likely. Crops out in the sun near players grow faster. |
| **Fall** | Gets colder. Leaves fall from trees, and late in fall grass and flowers die off. |
| **Winter** | Snowstorms in every biome that could plausibly snow. Snow piles up (3 layers by default, up to 8) and exposed water freezes. Deserts, savannas and badlands stay dry unless you're above Y=160. |

## Why nothing breaks
- **Seasonal snow and ice are the add-on's own blocks.** They look and work like the real ones, and they're the only snow and ice the add-on ever melts. Naturally generated snow, snow players place, and snowy biomes are never touched.
- **Frozen water turns back into the exact water source it was**, whether it thaws or gets mined. Water near farmland never freezes, so farmland can't dry out.
- **A dead plant remembers what it was.** Each type of grass or flower has its own withered block. It grows back into the same plant in spring, and if it's broken while dead, it drops the original plant's items.
- **Snow only settles on natural ground** (grass, dirt, stone, sand, leaves, logs...), never on slabs, chests or farmland.
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

For testing: `/scriptevent seasons:simulate <x> <z> <radius> <columns>` fast-forwards the seasonal block changes around a spot, and `/scriptevent seasons:probe <x> <z>` shows what the add-on sees there.

## Bedrock limits
- Bedrock decides rain vs. snow by biome, so winter snowstorms in warm biomes are drawn by the add-on. That means snowflake particles and snowstorm fog instead of vanilla rain.
- Bedrock can't change grass or leaf colors while the game is running, so autumn shows as falling leaves instead of recolored trees.
- Add-ons can't slow vanilla crop growth, so winter doesn't stop crops. Summer speeds them up.

## Development
`packs/DynamicSeasons_BP` contains the behavior pack (scripts in `scripts/`). `packs/DynamicSeasons_RP` contains the resource pack.
`python3 tools/build.py` regenerates the block, model, particle and manifest files and rebuilds `dist/DynamicSeasons.mcaddon`.
