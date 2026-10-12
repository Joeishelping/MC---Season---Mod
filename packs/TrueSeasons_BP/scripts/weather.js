import { system, world, WeatherType } from "@minecraft/server";
import { clearDurationMultiplier, currentSeason, stormDurationMultiplier, thunderChance } from "./clock.js";
import { getSettings } from "./settings.js";

/**
 * Seasonal weather. The add-on decides when storms start and stop and how long they last.
 * In spring/summer/fall a storm is vanilla rain (or thunder). In winter it is a snowstorm:
 * vanilla weather stays clear (Bedrock can only rain in plains/forests) and the add-on draws
 * the snow, piles it up and adds snowstorm fog.
 */
const KEY = "seasons:weather";
const MARGIN = 1200; // vanilla weather timer always outlasts ours so it never flips on its own

let state = { storm: false, snowstorm: false, timer: 0, intensity: 0.6 };
let ignoreEventsUntil = 0;

const randomBetween = (min, max) => min + Math.floor(Math.random() * (max - min + 1));
const rollStormLength = () => Math.round(randomBetween(12000, 24000) * stormDurationMultiplier());
const rollClearLength = () => Math.max(1200, Math.round(randomBetween(12000, 180000) * clearDurationMultiplier()));

function overworld() {
  return world.getDimension("overworld");
}

function save() {
  world.setDynamicProperty(KEY, JSON.stringify(state));
}

function setVanillaWeather(type, duration) {
  ignoreEventsUntil = system.currentTick + 5;
  try {
    overworld().setWeather(type, Math.max(20, Math.floor(duration)));
  } catch (e) {
    console.warn(`[True Seasons] Could not set weather: ${e}`);
  }
}

function applyStormWeather() {
  if (currentSeason() === "winter") {
    state.snowstorm = true;
    setVanillaWeather(WeatherType.Clear, state.timer + MARGIN);
  } else {
    state.snowstorm = false;
    const thunder = Math.random() < thunderChance();
    setVanillaWeather(thunder ? WeatherType.Thunder : WeatherType.Rain, state.timer + MARGIN);
  }
}

/**
 * How strong a storm is, 0.25 (light flurries / drizzle) to 1 (blizzard / downpour).
 * Winter storms are mostly light-to-moderate snow with the occasional blizzard.
 */
function rollIntensity() {
  const r = Math.random();
  if (currentSeason() === "winter") return r < 0.4 ? 0.35 : r < 0.8 ? 0.65 : 1.0;
  return 0.4 + Math.random() * 0.6;
}

export function startStorm(length = rollStormLength()) {
  state.storm = true;
  state.timer = length;
  state.intensity = rollIntensity();
  applyStormWeather();
  save();
}

export function endStorm(length = rollClearLength()) {
  state.storm = false;
  state.snowstorm = false;
  state.timer = length;
  setVanillaWeather(WeatherType.Clear, length + MARGIN);
  save();
}

export function loadWeather() {
  try {
    const raw = world.getDynamicProperty(KEY);
    if (typeof raw === "string") state = { ...state, ...JSON.parse(raw) };
  } catch {
    state = { storm: false, snowstorm: false, timer: 0 };
  }
  if (!Number.isFinite(state.timer) || state.timer <= 0) {
    endStorm();
  }
}

function weatherCycleEnabled() {
  return getSettings().seasonalWeather && world.gameRules.doWeatherCycle;
}

/** Run every 20 ticks. */
export function tickWeather() {
  if (!weatherCycleEnabled()) {
    if (state.snowstorm) {
      state.snowstorm = false;
      state.storm = false;
      save();
    }
    return;
  }
  state.timer -= 20;
  if (state.timer <= 0) {
    if (state.storm) endStorm();
    else startStorm();
  } else if (system.currentTick % 200 === 0) {
    save();
  }
}

export function onSeasonChanged() {
  if (!weatherCycleEnabled()) return;
  if (state.storm) {
    applyStormWeather(); // rain <-> snowstorm
  } else {
    // A long summer dry spell must not carry over into the rainy spring.
    state.timer = Math.min(state.timer, rollClearLength());
  }
  save();
}

/** Someone used /weather: go along with it. */
export function onVanillaWeatherChanged(event) {
  if (system.currentTick < ignoreEventsUntil || !weatherCycleEnabled()) return;
  if (!event.dimension.endsWith("overworld")) return;
  if (event.newWeather === WeatherType.Clear) {
    state.storm = false;
    state.snowstorm = false;
    state.timer = rollClearLength();
    save();
  } else if (currentSeason() === "winter") {
    startStorm(); // turns it into a snowstorm
  } else {
    state.intensity = rollIntensity();
    state.storm = true;
    state.snowstorm = false;
    state.timer = rollStormLength();
    save();
  }
}

export const isStorming = () => state.storm;
export const isSnowstorm = () => state.storm && state.snowstorm;
export const stormIntensity = () => (Number.isFinite(state.intensity) ? state.intensity : 0.6);
export const isBlizzard = () => isSnowstorm() && stormIntensity() >= 0.9;
