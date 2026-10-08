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

const SEASON_BLURB = {
  spring: "Heavy rain. Cooler places start snowy and thaw as it warms up. Snow melts and plants come back.",
  summer: "Rain and sunshine. Crops out in the sun grow faster. Summer storms.",
  fall: "Getting colder. Leaves fall, and late in the season grass and flowers die off.",
  winter: "Snow anywhere it can snow. It piles up, and water freezes. Everything comes back in spring.",
};

export function seasonLine() {
  const season = currentSeason();
  return `${SEASON_COLORS[season]}${SEASON_NAMES[season]}§r - Day ${dayOfSeason()}/${seasonLength()}`;
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
        `Day ${dayOfSeason()} of ${seasonLength()} - Year ${currentYear()}\n\n${SEASON_BLURB[season]}`
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
  });
  setSeasonLength(next.seasonLengthDays);
  player.sendMessage(`§aSeason settings saved.§r Each season now lasts ${next.seasonLengthDays} days. It is ${seasonLine()}.`);
}
