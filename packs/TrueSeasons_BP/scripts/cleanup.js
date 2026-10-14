import { system, world } from "@minecraft/server";
import { cleanColumn } from "./effects.js";

export const DEFAULT_CLEANUP_RADIUS = 128;
let running = false;

/**
 * Removes ALL seasonal snow and ice and brings every withered plant back, in the loaded area
 * around every player in the Overworld - whatever the season. Runs in the background over a
 * few seconds so the game does not freeze. Areas that are not loaded are cleaned by the regular
 * sweep the next time someone goes there.
 */
export function runCleanup(radius = DEFAULT_CLEANUP_RADIUS, onDone, centresOverride) {
  if (running) return false;
  running = true;
  const r = Math.max(8, Math.min(256, Math.floor(radius)));
  const centres =
    centresOverride ??
    world
      .getAllPlayers()
      .filter((p) => p.dimension.id === "minecraft:overworld")
      .map((p) => ({ x: Math.floor(p.location.x), z: Math.floor(p.location.z) }));
  const dimension = world.getDimension("overworld");
  const seen = new Set();
  let restored = 0;

  system.runJob(
    (function* job() {
      try {
        let n = 0;
        for (const c of centres) {
          for (let dx = -r; dx <= r; dx++) {
            for (let dz = -r; dz <= r; dz++) {
              const key = `${c.x + dx},${c.z + dz}`;
              if (seen.has(key)) continue;
              seen.add(key);
              restored += cleanColumn(dimension, c.x + dx, c.z + dz, true);
              if (++n % 64 === 0) yield;
            }
          }
        }
      } finally {
        running = false;
        onDone?.(restored);
      }
    })()
  );
  return true;
}
