import { airTemperatureOffset, currentSeason } from "./clock.js";
import { getSettings } from "./settings.js";

/** Vanilla's snow threshold: precipitation is snow below this temperature. */
export const SNOW_TEMPERATURE = 0.15;
/** Hot biomes this warm or warmer are dry (no rain/snow) - deserts, savannas, badlands. */
const HOT_DRY_TEMPERATURE = 1.0;

/**
 * Base temperatures of vanilla overworld biomes. Both the Bedrock identifiers and the Java-style
 * names are listed so the table keeps working if identifiers are renamed.
 */
const BIOME_TEMPERATURES = {
  plains: 0.8, sunflower_plains: 0.8, meadow: 0.5, cherry_grove: 0.5,
  forest: 0.7, flower_forest: 0.7, forest_hills: 0.7,
  birch_forest: 0.6, birch_forest_hills: 0.6, birch_forest_mutated: 0.6, birch_forest_hills_mutated: 0.6,
  old_growth_birch_forest: 0.6,
  roofed_forest: 0.7, roofed_forest_mutated: 0.7, dark_forest: 0.7, pale_garden: 0.7,
  taiga: 0.25, taiga_hills: 0.25, taiga_mutated: 0.25,
  mega_taiga: 0.3, mega_taiga_hills: 0.3, old_growth_pine_taiga: 0.3,
  redwood_taiga_mutated: 0.25, redwood_taiga_hills_mutated: 0.25, old_growth_spruce_taiga: 0.25,
  cold_taiga: -0.5, cold_taiga_hills: -0.5, cold_taiga_mutated: -0.5, snowy_taiga: -0.5,
  extreme_hills: 0.2, extreme_hills_mutated: 0.2, extreme_hills_plus_trees: 0.2,
  extreme_hills_plus_trees_mutated: 0.2, extreme_hills_edge: 0.2,
  windswept_hills: 0.2, windswept_forest: 0.2, windswept_gravelly_hills: 0.2,
  stone_beach: 0.2, stony_shore: 0.2,
  swampland: 0.8, swampland_mutated: 0.8, swamp: 0.8, mangrove_swamp: 0.8,
  jungle: 0.95, jungle_hills: 0.95, jungle_mutated: 0.95, jungle_edge: 0.95, jungle_edge_mutated: 0.95,
  sparse_jungle: 0.95, bamboo_jungle: 0.95, bamboo_jungle_hills: 0.95,
  desert: 2.0, desert_hills: 2.0, desert_mutated: 2.0,
  savanna: 2.0, savanna_plateau: 2.0, savanna_mutated: 2.0, savanna_plateau_mutated: 2.0, windswept_savanna: 2.0,
  mesa: 2.0, mesa_plateau: 2.0, mesa_plateau_stone: 2.0, mesa_plateau_mutated: 2.0,
  mesa_plateau_stone_mutated: 2.0, mesa_bryce: 2.0, badlands: 2.0, wooded_badlands: 2.0, eroded_badlands: 2.0,
  ice_plains: 0.0, ice_plains_spikes: 0.0, ice_mountains: 0.0, snowy_plains: 0.0, ice_spikes: 0.0,
  frozen_river: 0.0, frozen_ocean: 0.0, deep_frozen_ocean: 0.0, legacy_frozen_ocean: 0.0,
  cold_beach: 0.05, snowy_beach: 0.05,
  grove: -0.2, snowy_slopes: -0.3, jagged_peaks: -0.7, frozen_peaks: -0.7, stony_peaks: 1.0,
  river: 0.5, beach: 0.8, mushroom_island: 0.9, mushroom_island_shore: 0.9, mushroom_fields: 0.9,
  ocean: 0.5, deep_ocean: 0.5, cold_ocean: 0.5, deep_cold_ocean: 0.5,
  lukewarm_ocean: 0.5, deep_lukewarm_ocean: 0.5, warm_ocean: 0.5, deep_warm_ocean: 0.5,
  lush_caves: 0.5, dripstone_caves: 0.8, deep_dark: 0.8,
};

const unknownBiomes = new Set();

function biomeTemperature(biome) {
  const id = biome?.id ?? "";
  const name = id.includes(":") ? id.split(":")[1] : id;
  const known = BIOME_TEMPERATURES[name];
  if (known !== undefined) return known;
  if (!unknownBiomes.has(id)) {
    unknownBiomes.add(id);
    console.warn(`[True Seasons] Unknown biome '${id}', using tags to guess its climate.`);
  }
  try {
    if (biome.hasTags(["frozen"]) || biome.hasTags(["ice"])) return 0.0;
    if (biome.hasTags(["desert"]) || biome.hasTags(["mesa"]) || biome.hasTags(["savanna"])) return 2.0;
    if (biome.hasTags(["jungle"])) return 0.95;
    if (biome.hasTags(["taiga"])) return 0.25;
  } catch {
    // tags unavailable - fall through
  }
  return 0.6;
}

/** Vanilla temperature at a position (biome temperature, cooler higher up). */
export function baseTemperature(biome, y) {
  const t = biomeTemperature(biome);
  return y > 80 ? t - ((y - 80) * 0.05) / 40 : t;
}

export function isDryBiome(biome) {
  return biomeTemperature(biome) >= HOT_DRY_TEMPERATURE;
}

export function isNaturallyCold(biome, y) {
  return baseTemperature(biome, y) < SNOW_TEMPERATURE;
}

function applyOffset(base, offset) {
  const adjusted = base + offset;
  // Naturally cold places (snowy biomes, mountain tops) never get warmer than vanilla.
  return base < SNOW_TEMPERATURE ? Math.min(base, adjusted) : adjusted;
}

/** Air temperature right now: season, weather front and time of day. */
export function seasonalTemperature(biome, y) {
  return applyOffset(baseTemperature(biome, y), airTemperatureOffset());
}

/** Minecraft temperature -> degrees Celsius (0 C is where snow starts). */
export function toCelsius(t) {
  const c = t < 1 ? (t - SNOW_TEMPERATURE) * 30 : 25.5 + (t - 1) * 12;
  return Math.round(c);
}

/** Deserts/savannas/badlands high enough up, during winter. */
function isDryWinterSnow(y) {
  return currentSeason() === "winter" && y >= getSettings().drySnowLineY;
}

/** Is it snowing (rather than raining) here, given the season? */
export function snowsHere(biome, y) {
  if (isDryBiome(biome)) return isDryWinterSnow(y);
  return seasonalTemperature(biome, y) < SNOW_TEMPERATURE;
}

/** Does it rain or snow here at all? (Hot dry biomes stay dry unless above the snow line.) */
export function precipitatesHere(biome, y) {
  return !isDryBiome(biome) || isDryWinterSnow(y);
}

/**
 * True when this spot is cold ONLY because of the season, so vanilla would never put snow or
 * ice here. The add-on only ever places its own (reversible) blocks in such spots.
 */
export function isSeasonallyFrozen(biome, y) {
  if (isNaturallyCold(biome, y)) return false;
  if (isDryBiome(biome)) return isDryWinterSnow(y);
  return seasonalTemperature(biome, y) < SNOW_TEMPERATURE;
}

/**
 * Seasonal snow only lasts through late fall and winter. From the first day of spring it all
 * melts, whatever the temperature, so nothing lingers.
 */
export function canSnowSettle() {
  const season = currentSeason();
  return season === "fall" || season === "winter";
}

export function shouldThaw(biome, y) {
  return !canSnowSettle() || !isSeasonallyFrozen(biome, y);
}

/** The add-on no longer freezes water; any seasonal ice left from older versions melts. */
export function shouldIceThaw() {
  return true;
}

export function shouldWither(biome, y) {
  if (!getSettings().witherPlants || isDryBiome(biome) || isNaturallyCold(biome, y)) return false;
  const season = currentSeason();
  return (season === "fall" || season === "winter") && seasonalTemperature(biome, y) < SNOW_TEMPERATURE;
}

/**
 * Withered plants here should come back. Deliberately generous so nothing can get stuck dead:
 * always in summer, in spring once it is warm, and immediately when withering is off or the
 * spot could never wither in the first place.
 */
export function shouldRevive(biome, y) {
  if (!getSettings().witherPlants || isDryBiome(biome) || isNaturallyCold(biome, y)) return true;
  const season = currentSeason();
  if (season === "summer") return true;
  return season === "spring" && seasonalTemperature(biome, y) >= SNOW_TEMPERATURE;
}
