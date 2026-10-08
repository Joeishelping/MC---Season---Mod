import { PlayerPermissionLevel } from "@minecraft/server";
import { ActionFormData, ModalFormData } from "@minecraft/server-ui";
import {
  currentSeason,
  currentYear,
  dayOfSeason,
  SEASON_COLORS,
  SEASON_NAMES,
  seasonLength,
  setSeasonLength,
} from "./clock.js";
import { getSettings, MAX_SEASON_LENGTH, MIN_SEASON_LENGTH, saveSettings } from "./settings.js";
import { seasonalTemperature, toCelsius } from "./climate.js";
import { isBlizzard, isSnowstorm, isStorming } from "./weather.js";

const SEASON_BLURB = {
  spring: "Days are getting longer. Heavy rain and misty mornings. Cool places start snowy and thaw as it warms; lakes are the last to melt. Snow melts and plants come back.",
  summer: "Long days and short nights. Rain and sunshine, afternoon thunderstorms, fireflies at night. Crops in the sun grow faster.",
  fall: "Days are getting shorter. Misty mornings and falling leaves; the first frosts come at night, then grass and flowers die off.",
  winter: "Short days and long nights. Snow anywhere it can snow - from light flurries to blizzards. It piles up, and lakes freeze over. Mild spells can melt it for a while.",
};

export function seasonLine() {
  const season = currentSeason();
  return `${SEASON_COLORS[season]}${SEASON_NAMES[season]}§r - Day ${dayOfSeason()}/${seasonLength()}`;
}

/** "12°C" / "54°F" at the player's position, or "" if unknown (e.g. in the Nether). */
export function temperatureText(player) {
  try {
    if (player.dimension.id !== "minecraft:overworld") return "";
    const biome = player.dimension.getBiome(player.location);
    const c = toCelsius(seasonalTemperature(biome, player.location.y));
    const color = c <= 0 ? "§b" : c >= 28 ? "§c" : c >= 18 ? "§e" : "§a";
    return getSettings().fahrenheit ? `${color}${Math.round(c * 1.8 + 32)}°F§r` : `${color}${c}°C§r`;
  } catch {
    return "";
  }
}

function skyText() {
  if (!isStorming()) return "Clear skies";
  if (isBlizzard()) return "Blizzard";
  if (isSnowstorm()) return "Snowing";
  return "Stormy";
}

/** Action bar line while holding the almanac. */
export function hudLine(player) {
  const temp = temperatureText(player);
  return temp ? `${seasonLine()} §7|§r ${temp}` : seasonLine();
}

export function isOperator(player) {
  try {
    return player.playerPermissionLevel === PlayerPermissionLevel.Operator || player.commandPermissionLevel >= 1;
  } catch {
    return false;
  }
}

export async function showAlmanac(player) {
  const season = currentSeason();
  const form = new ActionFormData()
    .title("Season Almanac")
    .body(
      `${SEASON_COLORS[season]}§l${SEASON_NAMES[season]}§r\n` +
        `Day ${dayOfSeason()} of ${seasonLength()} - Year ${currentYear()}\n` +
        (temperatureText(player) ? `Temperature here: ${temperatureText(player)}\n` : "") +
        `${skyText()}\n\n${SEASON_BLURB[season]}`
    )
    .button("Close");
  const operator = isOperator(player);
  if (operator) form.button("Settings");
  const response = await form.show(player);
  if (!response.canceled && operator && response.selection === 1) await showSettings(player);
}

export async function showSettings(player) {
  if (!isOperator(player)) {
    player.sendMessage("§cOnly operators can change season settings.");
    return;
  }
  const s = getSettings();
  const form = new ModalFormData()
    .title("Dynamic Seasons")
    .slider("Season length (MC days) - all four seasons are equal", MIN_SEASON_LENGTH, MAX_SEASON_LENGTH, {
      defaultValue: s.seasonLengthDays,
      valueStep: 1,
    })
    .slider("Max snow depth (layers)", 1, 8, { defaultValue: s.maxSnowLayers, valueStep: 1 })
    .toggle("Seasonal weather", { defaultValue: s.seasonalWeather })
    .toggle("Water freezes in winter", { defaultValue: s.freezeWater })
    .toggle("Plants die off in the cold", { defaultValue: s.witherPlants })
    .toggle("Crops grow faster in summer", { defaultValue: s.cropGrowth })
    .toggle("Snowflakes, falling leaves and fog", { defaultValue: s.particles })
    .toggle("Announce new seasons", { defaultValue: s.announceSeasons })
    .toggle("Show season while holding the almanac", { defaultValue: s.showHud })
    .toggle("Long summer days, short winter days", { defaultValue: s.realisticDayLength })
    .toggle("Show temperature in Fahrenheit", { defaultValue: s.fahrenheit })
    .submitButton("Save");
  const response = await form.show(player);
  if (response.canceled || !response.formValues) return;
  const v = response.formValues;
  const next = saveSettings({
    ...s,
    seasonLengthDays: v[0],
    maxSnowLayers: v[1],
    seasonalWeather: v[2],
    freezeWater: v[3],
    witherPlants: v[4],
    cropGrowth: v[5],
    particles: v[6],
    announceSeasons: v[7],
    showHud: v[8],
    realisticDayLength: v[9],
    fahrenheit: v[10],
  });
  setSeasonLength(next.seasonLengthDays);
  player.sendMessage(`§aSeason settings saved.§r Each season now lasts ${next.seasonLengthDays} days. It is ${seasonLine()}.`);
}
