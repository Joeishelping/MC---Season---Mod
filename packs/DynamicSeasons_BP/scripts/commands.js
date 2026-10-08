import { CommandPermissionLevel, CustomCommandParamType, CustomCommandStatus, system } from "@minecraft/server";
import { SEASONS, SEASON_NAMES, seasonLength, setSeason, setSeasonLength, currentYear } from "./clock.js";
import { getSettings, MAX_SEASON_LENGTH, MIN_SEASON_LENGTH, saveSettings } from "./settings.js";
import { seasonLine, showSettings } from "./ui.js";
import { endStorm, startStorm } from "./weather.js";

/**
 * /seasons:season                      - current season (anyone; "/season" works too)
 * /seasons:setseason <season> [day]    - jump to a season (operators)
 * /seasons:seasonlength <days>         - change how long every season lasts (operators)
 * /seasons:seasonconfig                - open the settings menu (operators)
 * /seasons:storm <start|stop>          - start or stop a storm right now (operators)
 */
export function registerCommands(registry, onSeasonForced) {
  registry.registerEnum("seasons:season", SEASONS);
  registry.registerEnum("seasons:stormaction", ["start", "stop"]);

  registry.registerCommand(
    { name: "seasons:season", description: "Show the current season", permissionLevel: CommandPermissionLevel.Any },
    () => ({ status: CustomCommandStatus.Success, message: `It is ${seasonLine()} (year ${currentYear()}).` })
  );

  registry.registerCommand(
    {
      name: "seasons:setseason",
      description: "Jump to a season",
      permissionLevel: CommandPermissionLevel.GameDirectors,
      mandatoryParameters: [{ name: "seasons:season", type: CustomCommandParamType.Enum }],
      optionalParameters: [{ name: "day", type: CustomCommandParamType.Integer }],
    },
    (origin, season, day) => {
      const target = Math.max(1, Math.min(seasonLength(), day ?? 1));
      system.run(() => {
        setSeason(season, target);
        onSeasonForced();
      });
      return { status: CustomCommandStatus.Success, message: `Season set to ${SEASON_NAMES[season]}, day ${target}.` };
    }
  );

  registry.registerCommand(
    {
      name: "seasons:seasonlength",
      description: "Set how many MC days each season lasts",
      permissionLevel: CommandPermissionLevel.GameDirectors,
      mandatoryParameters: [{ name: "days", type: CustomCommandParamType.Integer }],
    },
    (origin, days) => {
      if (days < MIN_SEASON_LENGTH || days > MAX_SEASON_LENGTH) {
        return { status: CustomCommandStatus.Failure, message: `Days must be ${MIN_SEASON_LENGTH}-${MAX_SEASON_LENGTH}.` };
      }
      system.run(() => {
        const next = saveSettings({ ...getSettings(), seasonLengthDays: days });
        setSeasonLength(next.seasonLengthDays);
      });
      return { status: CustomCommandStatus.Success, message: `Each season now lasts ${days} days.` };
    }
  );

  registry.registerCommand(
    {
      name: "seasons:seasonconfig",
      description: "Open the Dynamic Seasons settings",
      permissionLevel: CommandPermissionLevel.GameDirectors,
    },
    (origin) => {
      const player = origin.sourceEntity;
      if (player?.typeId !== "minecraft:player") {
        return { status: CustomCommandStatus.Failure, message: "Run this as a player." };
      }
      // Wait a moment so the chat screen is closed before the menu opens.
      system.runTimeout(() => showSettings(player), 10);
      return { status: CustomCommandStatus.Success };
    }
  );

  registry.registerCommand(
    {
      name: "seasons:storm",
      description: "Start or stop a seasonal storm",
      permissionLevel: CommandPermissionLevel.GameDirectors,
      mandatoryParameters: [{ name: "seasons:stormaction", type: CustomCommandParamType.Enum }],
    },
    (origin, action) => {
      system.run(() => (action === "start" ? startStorm() : endStorm()));
      return { status: CustomCommandStatus.Success, message: action === "start" ? "Storm started." : "Storm stopped." };
    }
  );
}
