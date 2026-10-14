import { ItemStack, system, world } from "@minecraft/server";
import { registerBlockComponents } from "./blocks.js";
import { advanceClock, currentSeason, loadClock, SEASON_COLORS, SEASON_NAMES, saveClock } from "./clock.js";
import { registerCommands } from "./commands.js";
import { runCleanup } from "./cleanup.js";
import { tickDayLength } from "./daylight.js";
import { forgetPlayer, tickColumn, tickEffects } from "./effects.js";
import { getSettings } from "./settings.js";
import { hudLine, seasonLine, showAlmanac } from "./ui.js";
import { loadWeather, onSeasonChanged, onVanillaWeatherChanged, tickWeather } from "./weather.js";

const ALMANAC = "seasons:almanac";
let lastSeason;
let ready = false;

system.beforeEvents.startup.subscribe(({ blockComponentRegistry, customCommandRegistry }) => {
  registerBlockComponents(blockComponentRegistry);
  registerCommands(customCommandRegistry, () => {
    lastSeason = undefined; // force the change handling on the next tick
  });
});

function announce(season) {
  if (!getSettings().announceSeasons) return;
  for (const player of world.getAllPlayers()) {
    try {
      player.onScreenDisplay.setTitle(`${SEASON_COLORS[season]}${SEASON_NAMES[season]}`, {
        subtitle: "A new season begins",
        fadeInDuration: 20,
        stayDuration: 60,
        fadeOutDuration: 20,
      });
    } catch {
      // player may be loading
    }
  }
  world.sendMessage(`${SEASON_COLORS[season]}${SEASON_NAMES[season]}§r has begun.`);
}

function holdingAlmanac(player) {
  try {
    return player.getComponent("minecraft:equippable")?.getEquipment("Mainhand")?.typeId === ALMANAC;
  } catch {
    return false;
  }
}

function giveAlmanac(player) {
  if (player.getDynamicProperty("seasons:got_almanac")) return;
  try {
    const leftover = player.getComponent("minecraft:inventory")?.container?.addItem(new ItemStack(ALMANAC));
    if (leftover) player.dimension.spawnItem(leftover, player.location);
    player.setDynamicProperty("seasons:got_almanac", true);
  } catch {
    // try again next spawn
  }
}

system.run(() => {
  loadClock();
  loadWeather();
  lastSeason = currentSeason();
  ready = true;
  console.info(`[True Seasons] ${seasonLine()}`);
});

system.runInterval(() => {
  if (!ready) return;
  const tick = system.currentTick;
  tickDayLength();
  advanceClock();

  const season = currentSeason();
  if (season !== lastSeason) {
    announce(season);
    lastSeason = season;
    onSeasonChanged();
  }

  if (tick % 20 === 0) {
    tickWeather();
    if (getSettings().showHud) {
      for (const player of world.getAllPlayers()) {
        if (holdingAlmanac(player)) player.onScreenDisplay.setActionBar(hudLine(player));
      }
    }
  }
  if (tick % 100 === 0) saveClock();

  tickEffects(tick);
}, 1);

world.afterEvents.weatherChange.subscribe((event) => {
  if (ready) onVanillaWeatherChanged(event);
});

world.afterEvents.itemUse.subscribe(({ itemStack, source }) => {
  if (itemStack?.typeId === ALMANAC) system.run(() => showAlmanac(source));
});

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (initialSpawn) system.runTimeout(() => giveAlmanac(player), 40);
});

world.afterEvents.playerLeave.subscribe(({ playerId }) => forgetPlayer(playerId));

/**
 * Operator/debug tool (needs /scriptevent, which is operator-only):
 *   /scriptevent seasons:simulate <x> <z> <radius> <columns>
 * runs the seasonal column update on random columns around x/z, e.g. to preview a winter in a
 * ticking area without waiting.
 */
system.afterEvents.scriptEventReceive.subscribe(({ id, message, sourceEntity }) => {
  if (id === "seasons:cleanup_at") {
    // /scriptevent seasons:cleanup_at <x> <z> <radius> - clean-up around a spot (operators/debug)
    const [x, z, r] = message.trim().split(/\s+/).map(Number);
    runCleanup(
      r,
      (n, chunks) => console.info(`[True Seasons] clean-up restored ${n} block(s) in ${chunks} loaded chunk(s)`),
      [{ x, z }],
      (pct, n) => console.info(`[True Seasons] clean-up ${pct}% (${n})`)
    );
    return;
  }
  if (id === "seasons:probe") {
    probe(message, sourceEntity);
    return;
  }
  if (id !== "seasons:simulate") return;
  const [x, z, radius, columns] = message.trim().split(/\s+/).map(Number);
  if (![x, z, radius, columns].every(Number.isFinite)) {
    console.warn("[True Seasons] usage: /scriptevent seasons:simulate <x> <z> <radius> <columns>");
    return;
  }
  const dimension = sourceEntity?.dimension ?? world.getDimension("overworld");
  const settings = getSettings();
  for (let i = 0; i < Math.min(columns, 200000); i++) {
    const cx = Math.floor(x + (Math.random() * 2 - 1) * radius);
    const cz = Math.floor(z + (Math.random() * 2 - 1) * radius);
    tickColumn(dimension, cx, cz, settings);
  }
  console.info(`[True Seasons] simulated ${columns} columns around ${x} ${z}`);
});

/** /scriptevent seasons:probe <x> <z> - logs what the add-on sees at a column (operators/debug). */
function probe(message, sourceEntity) {
  const [x, z] = message.trim().split(/\s+/).map(Number);
  const dimension = sourceEntity?.dimension ?? world.getDimension("overworld");
  try {
    const top = dimension.getTopmostBlock({ x, z });
    const biome = top ? dimension.getBiome(top.location) : undefined;
    console.info(
      `[True Seasons] probe ${x} ${z}: top=${top?.typeId}@${top?.y} above=${top?.above()?.typeId} biome=${biome?.id}`
    );
  } catch (e) {
    console.warn(`[True Seasons] probe failed: ${e}`);
  }
}
