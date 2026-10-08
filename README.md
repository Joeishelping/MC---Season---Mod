# Dynamic Seasons (Java Edition, Fabric 1.21.1)

Spring, Summer, Fall and Winter. Everything the seasons change goes back to normal on its own.

## Requirements
Minecraft Java 1.21.1, Fabric Loader 0.16+, Fabric API. Mod Menu is optional (adds a config button).
Install on both the client and the server.

## Seasons
All four seasons are the same length, from 1 to 100 MC days (default 10). Change it with the slider
in the config screen (Mod Menu, `/seasonconfig`, or the "Open Seasons Config" key binding), or with `/season length <days>`.

- **Spring:** heavy rain. Cooler biomes start with snow that turns to rain as it warms up. Snow melts and dead plants come back.
- **Summer:** a mix of rain and sun, more thunderstorms, and crops grow 1.5x faster.
- **Fall:** gets colder, leaves turn orange, crops slow down. Late in fall, grass and flowers die off once it gets cold enough to snow.
- **Winter:** snow in every biome that could plausibly get it. It piles up (3 layers by default), exposed water freezes, and crops under open sky grow at 25%. Deserts, savannas and badlands only get snow above Y=160.

## Why nothing breaks
- Biome temperature itself is never changed, so world generation is identical in every season.
- Seasonal snow and ice are their own blocks, and they're the only snow and ice the mod ever melts. Naturally generated snow and snow placed by players are never touched.
- Seasonal ice always turns back into a water source. Farmland next to frozen water stays hydrated.
- A dead plant stores its exact original block and puts it back in spring. Broken early, it drops the original plant's items.
- Chunks nobody visited catch up the first time they're loaded.
- Setting the time backwards (`/time set day`) never rewinds a season. Sleeping moves seasons forward.

## Commands
`/season` shows the current season. `/season set <season> [day]` and `/season length <days>` require op.

## Building
`./gradlew build` creates the jar in `build/libs/`. `./gradlew runTestmodServer` starts a dev server with the `/seasontest` command.
