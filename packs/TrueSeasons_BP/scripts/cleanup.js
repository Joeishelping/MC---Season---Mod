import { system, world } from "@minecraft/server";
import { cleanColumn } from "./effects.js";

export const DEFAULT_CLEANUP_RADIUS = 256;
export const MAX_CLEANUP_RADIUS = 1024;

let running = false;

function isChunkLoaded(dimension, cx, cz) {
  try {
    return dimension.getBlock({ x: cx * 16 + 8, y: 64, z: cz * 16 + 8 }) !== undefined;
  } catch {
    return false;
  }
}

/**
 * Removes ALL seasonal snow and ice and brings every withered plant back in every loaded chunk
 * within `radius` blocks of every player in the Overworld (or of `centresOverride`), whatever
 * the season. Runs in the background; `onProgress` and `onDone` report back.
 *
 * Chunks that are not loaded are skipped on purpose - loading them would generate new terrain
 * in places nobody has explored. Seasonal snow left there melts by itself as soon as someone
 * goes near it outside winter, or right away when "Snow settles" is turned off.
 */
export function runCleanup(radius = DEFAULT_CLEANUP_RADIUS, onDone, centresOverride, onProgress) {
  if (running) return false;
  running = true;
  const r = Math.max(16, Math.min(MAX_CLEANUP_RADIUS, Math.floor(radius)));
  const centres =
    centresOverride ??
    world
      .getAllPlayers()
      .filter((p) => p.dimension.id === "minecraft:overworld")
      .map((p) => ({ x: Math.floor(p.location.x), z: Math.floor(p.location.z) }));
  const dimension = world.getDimension("overworld");

  // Chunks within the radius of any centre, nearest first.
  const chunks = new Map();
  for (const c of centres) {
    const rc = Math.ceil(r / 16);
    const ccx = Math.floor(c.x / 16);
    const ccz = Math.floor(c.z / 16);
    for (let cx = ccx - rc; cx <= ccx + rc; cx++) {
      for (let cz = ccz - rc; cz <= ccz + rc; cz++) {
        const d = Math.hypot(cx - ccx, cz - ccz);
        if (d > rc + 1) continue;
        const key = `${cx},${cz}`;
        if (!chunks.has(key) || chunks.get(key).d > d) chunks.set(key, { cx, cz, d });
      }
    }
  }
  const order = [...chunks.values()].sort((a, b) => a.d - b.d);

  let restored = 0;
  let cleanedChunks = 0;
  system.runJob(
    (function* job() {
      try {
        let done = 0;
        let nextReport = 0.25;
        for (const { cx, cz } of order) {
          done++;
          if (isChunkLoaded(dimension, cx, cz)) {
            cleanedChunks++;
            for (let dx = 0; dx < 16; dx++) {
              for (let dz = 0; dz < 16; dz++) {
                restored += cleanColumn(dimension, cx * 16 + dx, cz * 16 + dz, true);
              }
              yield;
            }
          } else if (done % 32 === 0) {
            yield;
          }
          if (onProgress && done / order.length >= nextReport && done < order.length) {
            onProgress(Math.round(nextReport * 100), restored);
            nextReport += 0.25;
          }
        }
      } finally {
        running = false;
        onDone?.(restored, cleanedChunks);
      }
    })()
  );
  return true;
}
