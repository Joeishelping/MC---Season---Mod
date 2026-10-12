import { MolangVariableMap, world } from "@minecraft/server";
import { currentSeason, currentTimeOfDay, isMistyDay } from "./clock.js";
import {
  isNaturallyCold,
  isDryBiome,
  isSeasonallyFrozen,
  isWaterSeasonallyFrozen,
  precipitatesHere,
  seasonalTemperature,
  shouldIceThaw,
  shouldRevive,
  shouldThaw,
  shouldWither,
  snowsHere,
} from "./climate.js";
import { LAYERS, meltSnow, REVIVES, revivePlant, SEASONAL_ICE, SEASONAL_SNOW, thawIce, WITHERS } from "./blocks.js";
import { getSettings } from "./settings.js";
import { isBlizzard, isSnowstorm, isStorming, stormIntensity } from "./weather.js";

/**
 * Everything that changes blocks happens around players (Bedrock only simulates chunks near
 * players anyway). Areas nobody visits stay exactly as they are until someone does.
 */
const COLUMN_RADIUS = 48;
const COLUMNS_PER_PLAYER_PER_TICK = 4;

/** Blocks seasonal snow can settle on top of. Player-made blocks like slabs/chests never get snow. */
const SNOW_SURFACES = new Set([
  "minecraft:grass_block", "minecraft:grass", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol",
  "minecraft:mycelium", "minecraft:dirt_with_roots", "minecraft:moss_block", "minecraft:pale_moss_block",
  "minecraft:stone", "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:andesite",
  "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:calcite", "minecraft:deepslate",
  "minecraft:gravel", "minecraft:sand", "minecraft:red_sand", "minecraft:sandstone", "minecraft:red_sandstone",
  "minecraft:clay", "minecraft:mud", "minecraft:packed_mud", "minecraft:terracotta", "minecraft:hardened_clay",
  "minecraft:snow", "minecraft:packed_ice",
]);

function canHoldSnow(block) {
  const id = block.typeId;
  if (SNOW_SURFACES.has(id)) return true;
  if (id.endsWith("_leaves") || id === "minecraft:leaves" || id === "minecraft:leaves2") return true;
  if (id.endsWith("_log") || id.endsWith("_wood")) return true;
  return id.endsWith("_terracotta") && !id.includes("glazed");
}

const DECIDUOUS_LEAVES = new Set([
  "minecraft:oak_leaves", "minecraft:birch_leaves", "minecraft:dark_oak_leaves", "minecraft:acacia_leaves",
  "minecraft:jungle_leaves", "minecraft:cherry_leaves", "minecraft:pale_oak_leaves",
]);

const CROPS = new Set([
  "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroot",
  "minecraft:melon_stem", "minecraft:pumpkin_stem",
]);

function safeBiome(dimension, location) {
  try {
    return dimension.getBiome(location);
  } catch {
    return undefined;
  }
}

/**
 * The real top block of a column. getTopmostBlock skips plants and water, so climb up through
 * anything that is not air to reach the actual surface (a flower, the water surface, ...).
 */
function topmost(dimension, x, z) {
  let top;
  try {
    top = dimension.getTopmostBlock({ x, z });
  } catch {
    return undefined; // unloaded / out of bounds
  }
  for (let steps = 0; top && steps < 96; steps++) {
    const above = top.above();
    if (!above || above.isAir) break;
    top = above;
  }
  return top;
}

function isWaterSource(block) {
  return block.typeId === "minecraft:water" && Number(block.permutation.getState("liquid_depth") ?? 0) === 0;
}

function nearFarmland(dimension, pos) {
  // Frozen water must never dry out farmland (dry, empty farmland turns into dirt for good).
  for (let dx = -4; dx <= 4; dx++) {
    for (let dz = -4; dz <= 4; dz++) {
      for (let dy = -1; dy <= 0; dy++) {
        try {
          const b = dimension.getBlock({ x: pos.x + dx, y: pos.y + dy, z: pos.z + dz });
          if (b?.typeId === "minecraft:farmland") return true;
        } catch {
          return true; // unloaded neighbour: be safe, do not freeze
        }
      }
    }
  }
  return false;
}

function canFreeze(dimension, block) {
  if (!isWaterSource(block)) return false;
  const neighbours = [block.north(), block.south(), block.east(), block.west()];
  if (neighbours.some((n) => n === undefined)) return false;
  // Like vanilla: freeze from the shore inwards, never the middle of open water first.
  if (neighbours.every((n) => n.typeId === "minecraft:water")) return false;
  return !nearFarmland(dimension, block.location);
}

/** One random column near a player: snow, ice, withering and the matching reversals. */
export function tickColumn(dimension, x, z, settings) {
  const top = topmost(dimension, x, z);
  if (!top) return;
  const biome = safeBiome(dimension, top.location);
  if (!biome) return;
  const id = top.typeId;
  const y = top.y;

  // Reversals first - they can always run, whatever the settings.
  if (id === SEASONAL_SNOW) {
    if (shouldThaw(biome, y)) {
      meltSnow(top);
    } else if (isStorming() && snowsHere(biome, y) && Math.random() < stormIntensity()) {
      const layers = Number(top.permutation.getState(LAYERS) ?? 1);
      if (layers < settings.maxSnowLayers) top.setPermutation(top.permutation.withState(LAYERS, layers + 1));
    }
    return;
  }
  if (id === SEASONAL_ICE) {
    if (shouldIceThaw(biome, y)) thawIce(top);
    return;
  }
  if (REVIVES[id]) {
    if (shouldRevive(biome, y)) revivePlant(top);
    return;
  }

  if (WITHERS[id]) {
    if (shouldWither(biome, y)) top.setType(WITHERS[id]);
    return;
  }

  if (settings.freezeWater && isWaterSeasonallyFrozen(biome, y) && canFreeze(dimension, top)) {
    top.setType(SEASONAL_ICE);
    return;
  }

  if (isStorming() && canHoldSnow(top) && Math.random() < stormIntensity()) {
    const above = top.above();
    if (above?.isAir && isSeasonallyFrozen(biome, above.y) && snowsHere(biome, above.y)) {
      above.setType(SEASONAL_SNOW);
    }
  }
}

function boostCrops(player, dimension) {
  const { x, y, z } = player.location;
  for (let i = 0; i < 48; i++) {
    const pos = {
      x: Math.floor(x) + Math.floor(Math.random() * 25) - 12,
      y: Math.floor(y) + Math.floor(Math.random() * 7) - 3,
      z: Math.floor(z) + Math.floor(Math.random() * 25) - 12,
    };
    let block;
    try {
      block = dimension.getBlock(pos);
    } catch {
      continue;
    }
    if (!block || !CROPS.has(block.typeId) || Math.random() > 0.35) continue;
    if (block.getSkyLightLevel() < 13) continue; // only crops out in the sun
    const growth = Number(block.permutation.getState("growth"));
    if (Number.isFinite(growth) && growth < 7) {
      block.setPermutation(block.permutation.withState("growth", growth + 1));
    }
  }
}

const LEAF_COLORS = [
  { red: 0.85, green: 0.42, blue: 0.12 },
  { red: 0.78, green: 0.2, blue: 0.1 },
  { red: 0.9, green: 0.7, blue: 0.2 },
  { red: 0.6, green: 0.35, blue: 0.15 },
];

function fallingLeaves(player, dimension) {
  const { x, y, z } = player.location;
  for (let i = 0; i < 16; i++) {
    const pos = {
      x: Math.floor(x) + Math.floor(Math.random() * 21) - 10,
      y: Math.floor(y) + Math.floor(Math.random() * 12) - 2,
      z: Math.floor(z) + Math.floor(Math.random() * 21) - 10,
    };
    let block;
    try {
      block = dimension.getBlock(pos);
    } catch {
      continue;
    }
    if (!block || !DECIDUOUS_LEAVES.has(block.typeId)) continue;
    const below = block.below();
    if (!below?.isAir) continue;
    const vars = new MolangVariableMap();
    vars.setColorRGB("variable.color", LEAF_COLORS[Math.floor(Math.random() * LEAF_COLORS.length)]);
    try {
      dimension.spawnParticle("seasons:falling_leaf", below.center(), vars);
    } catch {
      // particle failed (e.g. chunk unloading) - purely cosmetic
    }
  }
}

/** Which seasonal fog each player currently has ("" = none). */
const activeFog = new Map();

function setFog(player, fogId) {
  const current = activeFog.get(player.id) ?? "";
  if (current === fogId) return;
  try {
    if (current) player.runCommand("fog @s remove seasons_weather");
    if (fogId) player.runCommand(`fog @s push ${fogId} seasons_weather`);
    activeFog.set(player.id, fogId);
  } catch {
    // fog is cosmetic
  }
}

function isMorning(tod) {
  return tod >= 22500 || tod < 2000;
}

function isNight(tod) {
  return tod >= 13000 && tod < 22500;
}

function weatherVisuals(player, dimension, settings) {
  const loc = player.location;
  const top = topmost(dimension, Math.floor(loc.x), Math.floor(loc.z));
  const outdoors = !top || top.y < loc.y + 1;
  const biome = safeBiome(dimension, loc);
  const season = currentSeason();
  const tod = currentTimeOfDay();
  let snowing = false;
  if (outdoors && biome && isStorming() && precipitatesHere(biome, loc.y) && snowsHere(biome, loc.y)) {
    // In spring/fall storms vanilla already draws snow where it is naturally cold.
    snowing = isSnowstorm() || !isNaturallyCold(biome, loc.y);
  }
  if (!settings.particles) {
    setFog(player, "");
    return;
  }

  if (snowing) {
    const vars = new MolangVariableMap();
    vars.setFloat("variable.intensity", stormIntensity());
    vars.setFloat("variable.wind", isBlizzard() ? 3.5 : 0.6);
    try {
      player.spawnParticle("seasons:snowfall", { x: loc.x, y: loc.y + 10, z: loc.z }, vars);
    } catch {
      // cosmetic
    }
  }

  let fog = "";
  if (snowing && isSnowstorm()) {
    fog = isBlizzard() ? "seasons:blizzard" : "seasons:snowstorm";
  } else if (
    outdoors && biome && !isStorming() && !isDryBiome(biome) && (season === "spring" || season === "fall") &&
    isMorning(tod) && isMistyDay()
  ) {
    fog = "seasons:mist";
  }
  setFog(player, fog);

  // Fireflies on warm, clear summer nights.
  if (
    outdoors && biome && season === "summer" && !isStorming() && isNight(tod) && !isDryBiome(biome) &&
    seasonalTemperature(biome, loc.y) > 0.45
  ) {
    try {
      player.spawnParticle("seasons:fireflies", { x: loc.x, y: loc.y + 1, z: loc.z });
    } catch {
      // cosmetic
    }
  }
}

export function forgetPlayer(playerId) {
  activeFog.delete(playerId);
}

/** Every tick. */
export function tickEffects(tick) {
  const settings = getSettings();
  const season = currentSeason();
  for (const player of world.getAllPlayers()) {
    let dimension;
    try {
      dimension = player.dimension;
    } catch {
      continue;
    }
    if (dimension.id !== "minecraft:overworld") {
      setFog(player, "");
      continue;
    }
    const { x, z } = player.location;
    for (let i = 0; i < COLUMNS_PER_PLAYER_PER_TICK; i++) {
      const cx = Math.floor(x) + Math.floor(Math.random() * (COLUMN_RADIUS * 2 + 1)) - COLUMN_RADIUS;
      const cz = Math.floor(z) + Math.floor(Math.random() * (COLUMN_RADIUS * 2 + 1)) - COLUMN_RADIUS;
      try {
        tickColumn(dimension, cx, cz, settings);
      } catch (e) {
        console.warn(`[True Seasons] column tick failed: ${e}`);
      }
    }
    if (tick % 10 === 0) weatherVisuals(player, dimension, settings);
    if (tick % 10 === 5 && season === "fall" && settings.particles) fallingLeaves(player, dimension);
    if (tick % 20 === 0 && season === "summer" && settings.cropGrowth) boostCrops(player, dimension);
  }
}
