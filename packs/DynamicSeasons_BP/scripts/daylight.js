import { world } from "@minecraft/server";
import { daylightFraction } from "./clock.js";
import { getSettings } from "./settings.js";

/**
 * Real-life day length: long summer days and short winter nights, short winter days and long
 * winter nights. A full day/night cycle still takes the normal 20 minutes - only the split
 * between day and night changes. Time is nudged one tick at a time, so the sun moves smoothly.
 */
const DAY_END = 12000; // sunset; time 0 is sunrise
let carry = 0;

export function tickDayLength() {
  if (!getSettings().realisticDayLength || !world.gameRules.doDayLightCycle) {
    carry = 0;
    return;
  }
  const daylight = daylightFraction();
  const isDay = world.getTimeOfDay() < DAY_END;
  // Game ticks that should pass per real tick in this part of the day.
  const rate = isDay ? 0.5 / daylight : 0.5 / (1 - daylight);
  carry += rate - 1;
  let nudge = 0;
  if (carry >= 1) nudge = Math.floor(carry);
  else if (carry <= -1) nudge = Math.ceil(carry);
  if (nudge !== 0) {
    carry -= nudge;
    world.setAbsoluteTime(world.getAbsoluteTime() + nudge);
  }
}
