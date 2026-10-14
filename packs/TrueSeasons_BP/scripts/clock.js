import { world } from "@minecraft/server";
import { getSettings } from "./settings.js";

/**
 * The season clock. Seasons follow in-game time: the normal day/night cycle, sleeping and
 * /time add all move them forward. Setting the time backwards never rewinds a season.
 */
export const TICKS_PER_DAY = 24000;
export const SEASONS = ["spring", "summer", "fall", "winter"];
export const SEASON_NAMES = { spring: "Spring", summer: "Summer", fall: "Fall", winter: "Winter" };
export const SEASON_COLORS = { spring: "§a", summer: "§e", fall: "§6", winter: "§b" };

const ELAPSED_KEY = "seasons:elapsed";
const LENGTH_KEY = "seasons:length";

/**
 * Temperature offset key frames over the year. X = position in the year measured in seasons
 * (0 = first tick of spring, 4 = end of winter), Y = offset added to biome temperature.
 * Snow falls below 0.15, so -0.7 makes plains (0.8) snowy while jungles (0.95) and
 * deserts/savannas never freeze.
 */
const TEMPERATURE_CURVE = [
  [0.0, -0.7], // early spring: still cold, snow in cooler biomes turning to rain
  [1.0, 0.0], // late spring
  [1.5, 0.15], // mid summer: warmest
  [2.0, 0.0], // early fall
  [3.0, -0.7], // late fall: frost, plants die off
  [3.25, -0.8], // deep winter
  [3.75, -0.8],
  [4.0, -0.7],
];

const SALT_KEY = "seasons:salt";

let elapsed = 0;
let lengthDays = 10;
let lastClock;
let dirty = false;
let salt = 0;
let timeOfDay = 0;

function clockNow() {
  return world.getDay() * TICKS_PER_DAY + world.getTimeOfDay();
}

export function loadClock() {
  const storedElapsed = world.getDynamicProperty(ELAPSED_KEY);
  const storedLength = world.getDynamicProperty(LENGTH_KEY);
  elapsed = typeof storedElapsed === "number" && storedElapsed >= 0 ? storedElapsed : 0;
  const configured = getSettings().seasonLengthDays;
  if (typeof storedLength === "number" && storedLength >= 1 && storedLength !== configured) {
    elapsed = rescale(elapsed, storedLength, configured);
  }
  lengthDays = configured;
  const storedSalt = world.getDynamicProperty(SALT_KEY);
  salt = typeof storedSalt === "number" ? storedSalt : Math.floor(Math.random() * 1e9);
  world.setDynamicProperty(SALT_KEY, salt);
  timeOfDay = world.getTimeOfDay();
  lastClock = clockNow();
  dirty = true;
  saveClock();
}

/** Call every tick. Returns true when the clock moved. */
export function advanceClock() {
  timeOfDay = world.getTimeOfDay();
  const now = clockNow();
  const delta = lastClock === undefined ? 0 : now - lastClock;
  lastClock = now;
  if (delta > 0) {
    elapsed += delta;
    dirty = true;
    return true;
  }
  return false;
}

export function saveClock() {
  if (!dirty) return;
  world.setDynamicProperty(ELAPSED_KEY, elapsed);
  world.setDynamicProperty(LENGTH_KEY, lengthDays);
  dirty = false;
}

export const seasonTicks = () => lengthDays * TICKS_PER_DAY;
export const yearTicks = () => 4 * seasonTicks();
export const seasonLength = () => lengthDays;

/** Position in the year in "seasons", in [0, 4). */
export function yearPosition() {
  const year = yearTicks();
  return (((elapsed % year) + year) % year) / seasonTicks();
}

export function currentSeason() {
  return SEASONS[Math.min(3, Math.floor(yearPosition()))];
}

export function seasonProgress() {
  const pos = yearPosition();
  return pos - Math.floor(pos);
}

export function dayOfSeason() {
  return Math.min(lengthDays, Math.floor(seasonProgress() * lengthDays) + 1);
}

export function currentYear() {
  return Math.floor(elapsed / yearTicks()) + 1;
}

/** Jump to the start of `day` of `season` in the current year, keeping the time of day. */
export function setSeason(season, day) {
  const index = SEASONS.indexOf(season);
  if (index < 0) return;
  const clampedDay = Math.max(1, Math.min(lengthDays, Math.floor(day)));
  const yearStart = Math.floor(elapsed / yearTicks()) * yearTicks();
  const timeOfDay = elapsed % TICKS_PER_DAY;
  elapsed = yearStart + index * seasonTicks() + (clampedDay - 1) * TICKS_PER_DAY + timeOfDay;
  dirty = true;
  saveClock();
}

/** Converts an elapsed value between season lengths, keeping season, progress and year. */
function rescale(value, oldLength, newLength) {
  const oldYear = 4 * oldLength * TICKS_PER_DAY;
  const newYear = 4 * newLength * TICKS_PER_DAY;
  const years = Math.floor(value / oldYear);
  const fraction = (value % oldYear) / oldYear;
  return years * newYear + Math.round(fraction * newYear);
}

/** Changes the season length without changing the current season or progress through it. */
export function setSeasonLength(newLength) {
  if (newLength === lengthDays) return;
  elapsed = rescale(elapsed, lengthDays, newLength);
  lengthDays = newLength;
  dirty = true;
  saveClock();
}

function curveAt(pos) {
  const p = ((pos % 4) + 4) % 4;
  for (let i = 0; i < TEMPERATURE_CURVE.length - 1; i++) {
    const [ax, ay] = TEMPERATURE_CURVE[i];
    const [bx, by] = TEMPERATURE_CURVE[i + 1];
    if (p >= ax && p <= bx) return ay + ((by - ay) * (p - ax)) / (bx - ax);
  }
  return TEMPERATURE_CURVE[0][1];
}

/** The average seasonal temperature change for this point in the year. */
export function temperatureOffset() {
  return curveAt(yearPosition());
}

/** Deterministic pseudo-random value in [-1, 1] for a whole number (stable per world). */
function noise(n) {
  let h = (Math.imul(n | 0, 0x27d4eb2d) ^ salt) >>> 0;
  h = Math.imul(h ^ (h >>> 15), 0x85ebca6b) >>> 0;
  h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35) >>> 0;
  return ((h ^ (h >>> 16)) >>> 0) / 0x7fffffff - 1;
}

/**
 * Day-to-day weather fronts: some days are warmer or colder than normal for the season (a mild
 * spell in winter, an early frost in fall). Changes smoothly from one day to the next.
 */
export function weatherAnomaly() {
  const days = elapsed / TICKS_PER_DAY;
  const day = Math.floor(days);
  const t = days - day;
  const smooth = (1 - Math.cos(Math.PI * t)) / 2;
  return 0.12 * (noise(day) * (1 - smooth) + noise(day + 1) * smooth);
}

/** Warmest mid-afternoon, coldest just before dawn. */
export function diurnalOffset() {
  // Time 6000 is noon and 18000 midnight; peak warmth around 8000 (2 pm).
  return 0.07 * Math.cos((2 * Math.PI * (timeOfDay - 8000)) / TICKS_PER_DAY);
}

/** Air temperature change right now: season + weather front + time of day. */
export function airTemperatureOffset() {
  return temperatureOffset() + weatherAnomaly() + diurnalOffset();
}

/** Fraction of the 24-hour day that is daylight: about 2/3 at midsummer, 1/3 at midwinter. */
export function daylightFraction() {
  return 0.5 + (1 / 6) * Math.cos((2 * Math.PI * (yearPosition() - 1.5)) / 4);
}

export const currentTimeOfDay = () => timeOfDay;

/** Roughly 3 in 5 spring and fall mornings are misty (stable per day). */
export function isMistyDay() {
  return noise(Math.floor(elapsed / TICKS_PER_DAY) + 7919) > -0.2;
}

/** How long storms last, relative to vanilla. */
export function stormDurationMultiplier(season = currentSeason()) {
  return { spring: 1.5, summer: 1.0, fall: 1.0, winter: 1.25 }[season];
}

/** How long it stays dry between storms, relative to vanilla. Spring is very wet. */
export function clearDurationMultiplier(season = currentSeason()) {
  return { spring: 0.35, summer: 0.6, fall: 1.0, winter: 0.5 }[season];
}

/** Chance a (non-winter) storm is a thunderstorm. */
export function thunderChance(season = currentSeason()) {
  return { spring: 0.15, summer: 0.35, fall: 0.1, winter: 0 }[season];
}
