import { GameMode, ItemStack } from "@minecraft/server";
import { getSettings } from "./settings.js";
import { shouldIceThaw, shouldRevive, shouldThaw } from "./climate.js";

export const SEASONAL_SNOW = "seasons:seasonal_snow";
export const SEASONAL_ICE = "seasons:seasonal_ice";
export const LAYERS = "seasons:layers";

/**
 * Plants that die off in the cold -> the withered block that remembers them.
 * Each withered block knows exactly which plant it was, drops that plant's loot when broken,
 * and turns back into it when it warms up.
 */
export const WITHERS = {
  "minecraft:short_grass": "seasons:withered_short_grass",
  "minecraft:fern": "seasons:withered_fern",
  "minecraft:dandelion": "seasons:withered_dandelion",
  "minecraft:poppy": "seasons:withered_poppy",
  "minecraft:blue_orchid": "seasons:withered_blue_orchid",
  "minecraft:allium": "seasons:withered_allium",
  "minecraft:azure_bluet": "seasons:withered_azure_bluet",
  "minecraft:red_tulip": "seasons:withered_red_tulip",
  "minecraft:orange_tulip": "seasons:withered_orange_tulip",
  "minecraft:white_tulip": "seasons:withered_white_tulip",
  "minecraft:pink_tulip": "seasons:withered_pink_tulip",
  "minecraft:oxeye_daisy": "seasons:withered_oxeye_daisy",
  "minecraft:cornflower": "seasons:withered_cornflower",
  "minecraft:lily_of_the_valley": "seasons:withered_lily_of_the_valley",
};
export const REVIVES = Object.fromEntries(Object.entries(WITHERS).map(([plant, dead]) => [dead, plant]));

function biomeAt(block) {
  try {
    return block.dimension.getBiome(block.location);
  } catch {
    return undefined;
  }
}

/*
 * Every change below first re-checks that the block is still the seasonal block. A block can get
 * two random ticks in the same game tick, and the second one must never act on whatever the first
 * one turned it into.
 */

/** Melts seasonal snow - the whole pile at once, so thawed ground clears quickly. */
export function meltSnow(block) {
  if (block.typeId !== SEASONAL_SNOW) return;
  block.setType("minecraft:air");
}

/** Seasonal ice was always a water source - give the water back. */
export function thawIce(block) {
  if (block.typeId !== SEASONAL_ICE) return;
  block.setType("minecraft:water");
}

/** Puts the original plant back. */
export function revivePlant(block) {
  const original = REVIVES[block.typeId];
  if (original) block.setType(original);
}

function isCreative(player) {
  try {
    return player?.getGameMode() === GameMode.Creative;
  } catch {
    return false;
  }
}

function heldItemId(player) {
  try {
    return player?.getComponent("minecraft:equippable")?.getEquipment("Mainhand")?.typeId ?? "";
  } catch {
    return "";
  }
}

export function registerBlockComponents(registry) {
  registry.registerCustomComponent("seasons:seasonal_snow", {
    onRandomTick({ block }) {
      const biome = biomeAt(block);
      if (!getSettings().snowOnGround || (biome && shouldThaw(biome, block.y)) || block.below()?.isAir) {
        meltSnow(block);
      }
    },
    onPlayerBreak({ block, brokenBlockPermutation, player }) {
      // Same drops as vanilla snow: one snowball per layer, only with a shovel.
      if (isCreative(player) || !heldItemId(player).endsWith("_shovel")) return;
      const layers = Number(brokenBlockPermutation.getState(LAYERS) ?? 1);
      block.dimension.spawnItem(new ItemStack("minecraft:snowball", layers), block.center());
    },
  });

  registry.registerCustomComponent("seasons:seasonal_ice", {
    onRandomTick({ block }) {
      const biome = biomeAt(block);
      if (biome && shouldIceThaw(biome, block.y)) thawIce(block);
    },
    onPlayerBreak({ block }) {
      // Breaking seasonal ice always leaves the original water behind.
      if (block.isAir) block.setType("minecraft:water");
    },
  });

  registry.registerCustomComponent("seasons:withered_plant", {
    onRandomTick({ block }) {
      const biome = biomeAt(block);
      if (!biome || shouldRevive(biome, block.y)) revivePlant(block);
    },
  });
}
