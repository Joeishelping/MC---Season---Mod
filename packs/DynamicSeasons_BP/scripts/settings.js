import { world } from "@minecraft/server";

const KEY = "seasons:settings";

export const MIN_SEASON_LENGTH = 1;
export const MAX_SEASON_LENGTH = 100;

const DEFAULTS = {
  /** Length of each season in Minecraft days. All four seasons are always equal. */
  seasonLengthDays: 10,
  /** How deep seasonal snow can pile up (layers, 1-8). */
  maxSnowLayers: 3,
  /** Above this Y, deserts/savannas/badlands can get snow in winter. */
  drySnowLineY: 160,
  /** Seasons control how often and how long it rains / snows. */
  seasonalWeather: true,
  /** Exposed water freezes in winter and turns back into water in spring. */
  freezeWater: true,
  /** Grass and flowers die off in the cold and come back exactly as they were. */
  witherPlants: true,
  /** Crops near players grow faster in summer. */
  cropGrowth: true,
  /** Snowflakes, falling autumn leaves and snowstorm fog. */
  particles: true,
  /** Title + chat message when a new season starts. */
  announceSeasons: true,
  /** Show season and day on the action bar while holding the Season Almanac. */
  showHud: true,
};

let cached;

function clamp(value, min, max, fallback) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.min(max, Math.max(min, Math.round(n))) : fallback;
}

function sanitize(raw) {
  const s = { ...DEFAULTS, ...(raw ?? {}) };
  s.seasonLengthDays = clamp(s.seasonLengthDays, MIN_SEASON_LENGTH, MAX_SEASON_LENGTH, DEFAULTS.seasonLengthDays);
  s.maxSnowLayers = clamp(s.maxSnowLayers, 1, 8, DEFAULTS.maxSnowLayers);
  s.drySnowLineY = clamp(s.drySnowLineY, -64, 320, DEFAULTS.drySnowLineY);
  for (const key of Object.keys(DEFAULTS)) {
    if (typeof DEFAULTS[key] === "boolean") s[key] = Boolean(s[key]);
  }
  return s;
}

export function getSettings() {
  if (!cached) {
    let raw;
    try {
      const stored = world.getDynamicProperty(KEY);
      raw = typeof stored === "string" ? JSON.parse(stored) : undefined;
    } catch {
      raw = undefined; // corrupt value: fall back to defaults rather than break the world
    }
    cached = sanitize(raw);
  }
  return cached;
}

export function saveSettings(next) {
  cached = sanitize(next);
  world.setDynamicProperty(KEY, JSON.stringify(cached));
  return cached;
}
