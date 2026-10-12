#!/usr/bin/env python3
"""Generates the repetitive pack JSON/PNG files and packages dist/TrueSeasons.mcaddon.

Hand-written files (scripts, README) live in packs/ directly; everything this script writes is
regenerated on every run, so edit this script rather than the generated files.

Usage: python3 tools/build.py
"""
import json
import struct
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "packs" / "TrueSeasons_BP"
RP = ROOT / "packs" / "TrueSeasons_RP"
DIST = ROOT / "dist"

VERSION = [1, 1, 1]
MIN_ENGINE = [1, 21, 130]
BLOCK_FORMAT = "1.21.130"
SERVER_MODULE = "2.4.0"
SERVER_UI_MODULE = "2.0.0"

BP_HEADER_UUID = "fa393e97-7c91-446a-bd4b-be4c0cebca96"
BP_DATA_UUID = "b8d03deb-346c-4021-aed7-391cc5438ce7"
BP_SCRIPT_UUID = "6c4934d2-5f14-4119-82fc-7c8c120e9d90"
RP_HEADER_UUID = "2691c437-4feb-4949-9d9f-98b1404ba58f"
RP_MODULE_UUID = "537376b6-6f7f-43f4-9473-9156f4c5c9a3"

DESCRIPTION = "Spring, Summer, Fall and Winter. Seasonal weather, snow that piles up and melts away, plants that die off and come back exactly as they were."

# plant id -> (display name, loot kind)
PLANTS = {
    "short_grass": ("Grass", "seeds"),
    "fern": ("Fern", "seeds"),
    "dandelion": ("Dandelion", "self"),
    "poppy": ("Poppy", "self"),
    "blue_orchid": ("Blue Orchid", "self"),
    "allium": ("Allium", "self"),
    "azure_bluet": ("Azure Bluet", "self"),
    "red_tulip": ("Red Tulip", "self"),
    "orange_tulip": ("Orange Tulip", "self"),
    "white_tulip": ("White Tulip", "self"),
    "pink_tulip": ("Pink Tulip", "self"),
    "oxeye_daisy": ("Oxeye Daisy", "self"),
    "cornflower": ("Cornflower", "self"),
    "lily_of_the_valley": ("Lily of the Valley", "self"),
}

# Blocks a withered plant can stand on (same as the living plant).
PLANT_SOIL = [
    "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol",
    "minecraft:dirt_with_roots", "minecraft:moss_block", "minecraft:pale_moss_block", "minecraft:mud",
    "minecraft:muddy_mangrove_roots", "minecraft:farmland", "minecraft:mycelium",
]


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def write_png(path: Path, pixels) -> None:
    """pixels: list of rows of (r, g, b, a)."""
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def from_mask(mask, colors):
    return [[colors.get(ch, (0, 0, 0, 0)) for ch in row] for row in mask]


# ----------------------------------------------------------------------------------------------
# Manifests
# ----------------------------------------------------------------------------------------------

def manifests():
    write_json(BP / "manifest.json", {
        "format_version": 2,
        "header": {
            "name": "True Seasons",
            "description": DESCRIPTION,
            "uuid": BP_HEADER_UUID,
            "version": VERSION,
            "min_engine_version": MIN_ENGINE,
        },
        "modules": [
            {"type": "data", "uuid": BP_DATA_UUID, "version": VERSION},
            {"type": "script", "language": "javascript", "uuid": BP_SCRIPT_UUID, "version": VERSION,
             "entry": "scripts/main.js"},
        ],
        "dependencies": [
            {"module_name": "@minecraft/server", "version": SERVER_MODULE},
            {"module_name": "@minecraft/server-ui", "version": SERVER_UI_MODULE},
            {"uuid": RP_HEADER_UUID, "version": VERSION},
        ],
    })
    write_json(RP / "manifest.json", {
        "format_version": 2,
        "header": {
            "name": "True Seasons Resources",
            "description": DESCRIPTION,
            "uuid": RP_HEADER_UUID,
            "version": VERSION,
            "min_engine_version": MIN_ENGINE,
        },
        "modules": [{"type": "resources", "uuid": RP_MODULE_UUID, "version": VERSION}],
        "dependencies": [{"uuid": BP_HEADER_UUID, "version": VERSION}],
    })


# ----------------------------------------------------------------------------------------------
# Blocks
# ----------------------------------------------------------------------------------------------

def box(height):
    return {"origin": [-8, 0, -8], "size": [16, height, 16]}


def snow_block():
    permutations = []
    for layers in range(1, 9):
        height = layers * 2
        collision = (layers - 1) * 2  # like vanilla: one layer has no collision
        permutations.append({
            "condition": f"q.block_state('seasons:layers') == {layers}",
            "components": {
                "minecraft:geometry": f"geometry.seasons_snow_{layers}",
                "minecraft:selection_box": box(height),
                "minecraft:collision_box": box(collision) if collision > 0 else False,
            },
        })
    write_json(BP / "blocks" / "seasonal_snow.json", {
        "format_version": BLOCK_FORMAT,
        "minecraft:block": {
            "description": {
                "identifier": "seasons:seasonal_snow",
                "menu_category": {"category": "none"},
                "states": {"seasons:layers": [1, 2, 3, 4, 5, 6, 7, 8]},
            },
            "components": {
                "minecraft:geometry": "geometry.seasons_snow_1",
                "minecraft:material_instances": {"*": {"texture": "snow", "render_method": "opaque"}},
                "minecraft:selection_box": box(2),
                "minecraft:collision_box": False,
                "minecraft:destructible_by_mining": {"seconds_to_destroy": 0.15},
                "minecraft:destructible_by_explosion": {"explosion_resistance": 0.1},
                "minecraft:light_dampening": 0,
                "minecraft:map_color": "#f0fbfb",
                "minecraft:loot": "loot_tables/empty.json",
                "minecraft:replaceable": {},
                "seasons:seasonal_snow": {},
            },
            "permutations": permutations,
        },
    })


def ice_block():
    write_json(BP / "blocks" / "seasonal_ice.json", {
        "format_version": BLOCK_FORMAT,
        "minecraft:block": {
            "description": {"identifier": "seasons:seasonal_ice", "menu_category": {"category": "none"}},
            "components": {
                "minecraft:geometry": "geometry.seasons_full_block",
                "minecraft:material_instances": {"*": {"texture": "ice", "render_method": "blend"}},
                "minecraft:friction": 0.02,
                "minecraft:light_dampening": 2,
                "minecraft:destructible_by_mining": {"seconds_to_destroy": 0.5},
                "minecraft:destructible_by_explosion": {"explosion_resistance": 0.5},
                "minecraft:map_color": "#a0a0ff",
                "minecraft:loot": "loot_tables/empty.json",
                "seasons:seasonal_ice": {},
            },
        },
    })


def withered_blocks():
    for plant, (name, loot) in PLANTS.items():
        identifier = f"seasons:withered_{plant}"
        components = {
            "minecraft:geometry": "geometry.seasons_cross",
            "minecraft:material_instances": {
                "*": {"texture": "deadbush", "render_method": "alpha_test", "ambient_occlusion": False,
                      "face_dimming": False},
            },
            "minecraft:collision_box": False,
            "minecraft:selection_box": {"origin": [-6, 0, -6], "size": [12, 13, 12]},
            "minecraft:destructible_by_mining": {"seconds_to_destroy": 0},
            "minecraft:destructible_by_explosion": {"explosion_resistance": 0},
            "minecraft:light_dampening": 0,
            "minecraft:map_color": "#94753e",
            "minecraft:loot": f"loot_tables/blocks/withered_{plant}.json",
            "minecraft:placement_filter": {
                "conditions": [{"allowed_faces": ["up"], "block_filter": PLANT_SOIL}],
            },
            "minecraft:liquid_detection": {
                "detection_rules": [{"liquid_type": "water", "on_liquid_touches": "broken"}],
            },
            "seasons:withered_plant": {},
        }
        if loot == "seeds":
            components["minecraft:replaceable"] = {}
        write_json(BP / "blocks" / f"withered_{plant}.json", {
            "format_version": BLOCK_FORMAT,
            "minecraft:block": {
                "description": {"identifier": identifier, "menu_category": {"category": "none"}},
                "components": components,
            },
        })
        if loot == "seeds":
            pools = [{"rolls": 1, "entries": [
                {"type": "item", "name": "minecraft:wheat_seeds", "weight": 1},
                {"type": "empty", "weight": 7},
            ]}]
        else:
            pools = [{"rolls": 1, "entries": [{"type": "item", "name": f"minecraft:{plant}"}]}]
        write_json(BP / "loot_tables" / "blocks" / f"withered_{plant}.json", {"pools": pools})
    write_json(BP / "loot_tables" / "empty.json", {"pools": []})


def items_and_recipes():
    write_json(BP / "items" / "almanac.json", {
        "format_version": BLOCK_FORMAT,
        "minecraft:item": {
            "description": {"identifier": "seasons:almanac", "menu_category": {"category": "items"}},
            "components": {
                "minecraft:icon": "seasons_almanac",
                "minecraft:display_name": {"value": "Season Almanac"},
                "minecraft:max_stack_size": 1,
                "minecraft:glint": True,
            },
        },
    })
    write_json(BP / "recipes" / "almanac.json", {
        "format_version": "1.20.10",
        "minecraft:recipe_shapeless": {
            "description": {"identifier": "seasons:almanac"},
            "tags": ["crafting_table"],
            "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:clock"}],
            "unlock": [{"item": "minecraft:book"}, {"item": "minecraft:clock"}],
            "result": {"item": "seasons:almanac"},
        },
    })


# ----------------------------------------------------------------------------------------------
# Resource pack
# ----------------------------------------------------------------------------------------------

def cube(height):
    def face(v, h):
        return {"uv": [0, v], "uv_size": [16, h]}

    side = face(16 - height, height)
    return {
        "origin": [-8, 0, -8], "size": [16, height, 16],
        "uv": {"north": side, "south": side, "east": side, "west": side,
               "up": face(0, 16), "down": face(0, 16)},
    }


def geometry():
    geos = [{
        "description": {"identifier": f"geometry.seasons_snow_{layers}", "texture_width": 16, "texture_height": 16},
        "bones": [{"name": "snow", "pivot": [0, 0, 0], "cubes": [cube(layers * 2)]}],
    } for layers in range(1, 9)]
    geos.append({
        "description": {"identifier": "geometry.seasons_full_block", "texture_width": 16, "texture_height": 16},
        "bones": [{"name": "block", "pivot": [0, 0, 0], "cubes": [cube(16)]}],
    })
    plane = {"north": {"uv": [0, 0], "uv_size": [16, 16]}, "south": {"uv": [16, 0], "uv_size": [-16, 16]}}
    geos.append({
        "description": {"identifier": "geometry.seasons_cross", "texture_width": 16, "texture_height": 16},
        "bones": [{"name": "cross", "pivot": [0, 0, 0], "cubes": [
            {"origin": [-8, 0, 0], "size": [16, 16, 0], "pivot": [0, 0, 0], "rotation": [0, 45, 0], "uv": plane},
            {"origin": [-8, 0, 0], "size": [16, 16, 0], "pivot": [0, 0, 0], "rotation": [0, -45, 0], "uv": plane},
        ]}],
    })
    write_json(RP / "models" / "blocks" / "seasons.geo.json", {"format_version": "1.12.0", "minecraft:geometry": geos})


def particles():
    write_json(RP / "particles" / "snowfall.json", {
        "format_version": "1.10.0",
        "particle_effect": {
            "description": {
                "identifier": "seasons:snowfall",
                "basic_render_parameters": {"material": "particles_alpha", "texture": "textures/particle/seasons_snowflake"},
            },
            "components": {
                # variable.intensity: 0.25 light flurries .. 1.0 blizzard; variable.wind: sideways push
                "minecraft:emitter_rate_steady": {"spawn_rate": "40 + 220 * variable.intensity", "max_particles": 1200},
                "minecraft:emitter_lifetime_once": {"active_time": 0.55},
                "minecraft:emitter_shape_box": {"half_dimensions": [16, 4, 16], "direction": [0, -1, 0]},
                "minecraft:particle_initial_speed": 2.0,
                "minecraft:particle_lifetime_expression": {"max_lifetime": 7},
                "minecraft:particle_motion_dynamic": {
                    "linear_acceleration": [
                        "variable.wind + math.sin(v.particle_age * 90 + v.particle_random_1 * 360) * 0.8",
                        -1.2,
                        "math.cos(v.particle_age * 70 + v.particle_random_2 * 360) * 0.8",
                    ],
                    "linear_drag_coefficient": 0.6,
                },
                "minecraft:particle_motion_collision": {"collision_radius": 0.05, "expire_on_contact": True},
                "minecraft:particle_appearance_billboard": {
                    "size": ["0.045 + v.particle_random_3 * 0.04", "0.045 + v.particle_random_3 * 0.04"],
                    "facing_camera_mode": "rotate_xyz",
                    "uv": {"texture_width": 8, "texture_height": 8, "uv": [0, 0], "uv_size": [8, 8]},
                },
                "minecraft:particle_appearance_lighting": {},
            },
        },
    })
    write_json(RP / "particles" / "falling_leaf.json", {
        "format_version": "1.10.0",
        "particle_effect": {
            "description": {
                "identifier": "seasons:falling_leaf",
                "basic_render_parameters": {"material": "particles_alpha", "texture": "textures/particle/seasons_leaf"},
            },
            "components": {
                "minecraft:emitter_rate_instant": {"num_particles": 1},
                "minecraft:emitter_lifetime_once": {"active_time": 0.1},
                "minecraft:emitter_shape_box": {"half_dimensions": [0.45, 0.1, 0.45], "direction": [0, -1, 0]},
                "minecraft:particle_initial_speed": 0.3,
                "minecraft:particle_lifetime_expression": {"max_lifetime": "5 + v.particle_random_1 * 3"},
                "minecraft:particle_initial_spin": {"rotation": "v.particle_random_2 * 360", "rotation_rate": 60},
                "minecraft:particle_motion_dynamic": {
                    "linear_acceleration": [
                        "math.sin(v.particle_age * 120 + v.particle_random_2 * 360) * 1.5",
                        -0.7,
                        "math.cos(v.particle_age * 100 + v.particle_random_3 * 360) * 0.9",
                    ],
                    "linear_drag_coefficient": 1.2,
                },
                "minecraft:particle_motion_collision": {"collision_radius": 0.05, "expire_on_contact": True},
                "minecraft:particle_appearance_billboard": {
                    "size": [0.09, 0.09],
                    "facing_camera_mode": "rotate_xyz",
                    "uv": {"texture_width": 8, "texture_height": 8, "uv": [0, 0], "uv_size": [8, 8]},
                },
                "minecraft:particle_appearance_tinting": {"color": ["v.color.r", "v.color.g", "v.color.b", 1.0]},
                "minecraft:particle_appearance_lighting": {},
            },
        },
    })


def fireflies():
    write_json(RP / "particles" / "fireflies.json", {
        "format_version": "1.10.0",
        "particle_effect": {
            "description": {
                "identifier": "seasons:fireflies",
                "basic_render_parameters": {"material": "particles_add", "texture": "textures/particle/seasons_firefly"},
            },
            "components": {
                "minecraft:emitter_rate_instant": {"num_particles": 2},
                "minecraft:emitter_lifetime_once": {"active_time": 0.1},
                "minecraft:emitter_shape_box": {"half_dimensions": [12, 2.5, 12], "direction": "outwards"},
                "minecraft:particle_initial_speed": 0.2,
                "minecraft:particle_lifetime_expression": {"max_lifetime": "4 + v.particle_random_1 * 3"},
                "minecraft:particle_motion_dynamic": {
                    "linear_acceleration": [
                        "math.sin(v.particle_age * 70 + v.particle_random_2 * 360) * 0.4",
                        "math.sin(v.particle_age * 50 + v.particle_random_3 * 360) * 0.25",
                        "math.cos(v.particle_age * 60 + v.particle_random_1 * 360) * 0.4",
                    ],
                    "linear_drag_coefficient": 1.5,
                },
                "minecraft:particle_appearance_billboard": {
                    "size": [0.07, 0.07],
                    "facing_camera_mode": "rotate_xyz",
                    "uv": {"texture_width": 8, "texture_height": 8, "uv": [0, 0], "uv_size": [8, 8]},
                },
                # slow blink
                "minecraft:particle_appearance_tinting": {
                    "color": [0.85, 1.0, 0.35, "math.clamp(math.sin(v.particle_age * 120 + v.particle_random_2 * 360) * 1.5, 0, 1)"],
                },
            },
        },
    })


def textures_and_text():
    snow = from_mask([
        "........",
        "...W....",
        ".W.W.W..",
        "..WWW...",
        "WWWWWWW.",
        "..WWW...",
        ".W.W.W..",
        "...W....",
    ], {"W": (255, 255, 255, 235)})
    write_png(RP / "textures" / "particle" / "seasons_snowflake.png", snow)
    leaf = from_mask([
        "......LL",
        "....LLLL",
        "..LLLLL.",
        ".LLLLDL.",
        ".LLLDLL.",
        "LLLDLL..",
        "LLDLL...",
        "D.......",
    ], {"L": (255, 255, 255, 255), "D": (200, 200, 200, 255)})
    write_png(RP / "textures" / "particle" / "seasons_leaf.png", leaf)
    glow = [[(255, 255, 255, max(0, 255 - int(((x - 3.5) ** 2 + (y - 3.5) ** 2) * 28))) for x in range(8)] for y in range(8)]
    write_png(RP / "textures" / "particle" / "seasons_firefly.png", glow)

    # Pack icon: the four seasons as quadrants.
    colors = [(120, 200, 80, 255), (240, 200, 60, 255), (215, 110, 40, 255), (220, 235, 245, 255)]
    icon = [[colors[(y >= 32) * 2 + (x >= 32)] for x in range(64)] for y in range(64)]
    write_png(BP / "pack_icon.png", icon)
    write_png(RP / "pack_icon.png", icon)

    write_json(RP / "textures" / "item_texture.json", {
        "resource_pack_name": "true_seasons",
        "texture_name": "atlas.items",
        "texture_data": {"seasons_almanac": {"textures": "textures/items/book_normal"}},
    })
    fogs = {
        "snowstorm": (12, 80, "#c9d2db"),  # steady snow
        "blizzard": (2, 28, "#dfe6ec"),  # white-out
        "mist": (6, 56, "#d6dde2"),  # spring/fall morning mist
    }
    for name, (start, end, color) in fogs.items():
        write_json(RP / "fogs" / f"{name}.json", {
            "format_version": "1.16.100",
            "minecraft:fog_settings": {
                "description": {"identifier": f"seasons:{name}"},
                "distance": {
                    "air": {"fog_start": start, "fog_end": end, "fog_color": color, "render_distance_type": "fixed"},
                },
            },
        })
    write_json(RP / "blocks.json", {
        "format_version": "1.21.40",
        "seasons:seasonal_snow": {"sound": "snow"},
        "seasons:seasonal_ice": {"sound": "glass"},
        **{f"seasons:withered_{p}": {"sound": "grass"} for p in PLANTS},
    })
    lang = ["## True Seasons", "item.seasons:almanac=Season Almanac",
            "tile.seasons:seasonal_snow.name=Snow", "tile.seasons:seasonal_ice.name=Ice"]
    lang += [f"tile.seasons:withered_{p}.name=Withered {name}" for p, (name, _) in PLANTS.items()]
    (RP / "texts").mkdir(parents=True, exist_ok=True)
    (RP / "texts" / "en_US.lang").write_text("\n".join(lang) + "\n", encoding="utf-8")
    write_json(RP / "texts" / "languages.json", ["en_US"])


def package():
    DIST.mkdir(exist_ok=True)
    target = DIST / "TrueSeasons.mcaddon"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as zf:
        for pack in (BP, RP):
            for path in sorted(pack.rglob("*")):
                if path.is_file():
                    zf.write(path, Path(pack.name) / path.relative_to(pack))
    print(f"Wrote {target.relative_to(ROOT)} ({target.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    manifests()
    snow_block()
    ice_block()
    withered_blocks()
    items_and_recipes()
    geometry()
    particles()
    fireflies()
    textures_and_text()
    package()
