"""Builds the files to share, into dist/:

  FreshTracks-<v>.mrpack        one-file modpack: import into Prism Launcher or the Modrinth App (Minecraft 26.3 +
                                Fabric + Fabric API + Fresh Tracks, set up in its own instance)
  FreshTracks-<v>-server.zip    for a server: mods/ (Fresh Tracks + Fabric API) and start scripts
  freshtracks-<v>.jar           the mod alone, for a manual install

Run after `./gradlew build`:  uv run python tools/package.py
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DIST = os.path.join(ROOT, "dist")
MINECRAFT = "26.3"
LOADER = "0.19.5"
INSTALLER = "1.1.2"
FABRIC_API = "0.162.0+26.3"
FABRIC_API_URL = "https://cdn.modrinth.com/data/P7dR8mSH/versions/v2j28coa/fabric-api-0.162.0%2B26.3.jar"
# Optional look-good mods offered in the pack (file name -> Modrinth CDN url), checked against visual-mods/.
VISUAL_MODS = {
    "sodium-fabric-0.9.2+mc26.3.jar": "https://cdn.modrinth.com/data/AANobbMI/versions/bAZQdGpg/sodium-fabric-0.9.2%2Bmc26.3.jar",
    "iris-fabric-1.11.7+mc26.3.jar": "https://cdn.modrinth.com/data/YL57xq9U/versions/vTN4NRGW/iris-fabric-1.11.7%2Bmc26.3.jar",
    "DistantHorizons-3.3.4-26.3-fabric-neoforge.jar": "https://cdn.modrinth.com/data/uCdwusMi/versions/TGgEbP9A/DistantHorizons-3.3.4-26.3-fabric-neoforge.jar",
    # performance (also run on the Snowmass server)
    "lithium-fabric-0.26.2+mc26.3.jar": "https://cdn.modrinth.com/data/gvQqBUqZ/versions/xS0Q8LSi/lithium-fabric-0.26.2%2Bmc26.3.jar",
    "ferritecore-9.0.0-fabric.jar": "https://cdn.modrinth.com/data/uXXizFIs/versions/d5ddUdiB/ferritecore-9.0.0-fabric.jar",
}
# The shader pack Iris loads by default (Complementary Unbound), downloaded by the launcher from Modrinth.
SHADER_PACK = ("ComplementaryUnbound_r5.9.3.zip", "https://cdn.modrinth.com/data/R6NEzAwj/versions/B1kyfoUZ/ComplementaryUnbound_r5.9.3.zip")
IRIS_PROPERTIES = """# Fresh Tracks pack: Complementary Unbound on by default with Iris (press K in game to toggle shaders)
enableShaders=true
shaderPack=ComplementaryUnbound_r5.9.3.zip
maxShadowRenderDistance=32
"""
# Optional mods that work on both sides (the Snowmass server runs them): proximity voice chat on the slopes.
SHARED_MODS = {
    "voicechat-fabric-2.6.24+26.3.jar": "https://cdn.modrinth.com/data/9eGKb6K1/versions/OLnMVWXy/voicechat-fabric-2.6.24%2B26.3.jar",
}
FABRIC_API_SHA1 = "273cd2dcbd92d1559edcc91c9f23fee47f6ff93f"


def version() -> str:
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        return re.search(r"^version=(.+)$", f.read(), re.M).group(1).strip()


def fabric_api_jar() -> str:
    cache = os.path.expanduser(os.path.join("~", ".gradle", "caches", "modules-2", "files-2.1", "net.fabricmc.fabric-api",
                                            "fabric-api", FABRIC_API))
    for dirpath, _, files in os.walk(cache):
        for name in files:
            if name == f"fabric-api-{FABRIC_API}.jar":
                path = os.path.join(dirpath, name)
                if sha(path, "sha1") == FABRIC_API_SHA1:
                    return path
    raise SystemExit("Fabric API jar not found in the Gradle cache (run ./gradlew build first)")


def sha(path: str, algo: str) -> str:
    h = hashlib.new(algo)
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


START_BAT = """@echo off
rem Fresh Tracks server. Needs Java 25 (https://adoptium.net). First run: read eula.txt, set eula=true, run again.
if not exist fabric-server-launch.jar (
  echo Downloading the Fabric server launcher for Minecraft {mc}...
  curl -L -o fabric-server-launch.jar https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/{installer}/server/jar
)
java -Xmx4G -jar fabric-server-launch.jar nogui
pause
"""

START_SH = """#!/bin/sh
# Fresh Tracks server. Needs Java 25. First run: read eula.txt, set eula=true, run again.
cd "$(dirname "$0")"
if [ ! -f fabric-server-launch.jar ]; then
  echo "Downloading the Fabric server launcher for Minecraft {mc}..."
  curl -L -o fabric-server-launch.jar https://meta.fabricmc.net/v2/versions/loader/{mc}/{loader}/{installer}/server/jar
fi
exec java -Xmx4G -jar fabric-server-launch.jar nogui
"""

SERVER_README = r"""Fresh Tracks {v} server
========================

1. Install Java 25 on the server machine (https://adoptium.net).
2. Unzip this folder where you want the server to live.
3. Double-click start.bat (Windows) or run ./start.sh (Mac/Linux). The first time it downloads the Fabric server
   launcher for Minecraft {mc} and then stops, asking you to accept Mojang's EULA:
   open eula.txt, read it, change eula=false to eula=true, and start again.
4. Friends join with the Fresh Tracks modpack (or Minecraft {mc} + Fabric + Fabric API + Fresh Tracks).
   Everyone needs the same Fresh Tracks version as the server.

Settings: config/freshtracks.json appears after the first start (weather events, cold, powder build-up, lift
speed, wind, village shops...). Edit, then restart.

Mountains made for skiing: before the first world is made, open server.properties and set
  level-type=freshtracks\:ski_mountains

The test world (FreshTracks-{v}-TestWorld.zip): unzip it in here and set
  level-name=Fresh Tracks Test World

To use an existing world: copy the world folder in here and set level-name in server.properties to its folder name.
Back it up first.
"""


def main() -> None:
    v = version()
    jar = os.path.join(ROOT, "build", "libs", f"freshtracks-{v}.jar")
    if not os.path.exists(jar):
        raise SystemExit(f"{jar} missing: run ./gradlew build first")
    api = fabric_api_jar()
    os.makedirs(DIST, exist_ok=True)
    for name in os.listdir(DIST):
        if name.startswith(("FreshTracks-", "freshtracks-")) and not name.endswith("-TestWorld.zip"):
            os.remove(os.path.join(DIST, name))

    # 1. the mod alone
    shutil.copy2(jar, os.path.join(DIST, f"freshtracks-{v}.jar"))

    # 2. Modrinth modpack (.mrpack): Fabric API from Modrinth's CDN, Fresh Tracks inside the pack
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": v,
        "name": "Fresh Tracks",
        "summary": "Skis, snowboards, chairlifts, powder days and alpine biomes for Minecraft " + MINECRAFT,
        "files": [{
            "path": f"mods/fabric-api-{FABRIC_API}.jar",
            "hashes": {"sha1": sha(api, "sha1"), "sha512": sha(api, "sha512")},
            "env": {"client": "required", "server": "required"},
            "downloads": [FABRIC_API_URL],
            "fileSize": os.path.getsize(api),
        }],
        "dependencies": {"minecraft": MINECRAFT, "fabric-loader": LOADER},
    }
    # Optional, client only: Sodium + Iris (shaders) + Distant Horizons. Launchers download them from Modrinth's CDN
    # (nothing is redistributed) and let players untick them. Hashes come from the copies in visual-mods/.
    visual = os.path.join(ROOT, "visual-mods")
    for name, url in VISUAL_MODS.items():
        path = os.path.join(visual, name)
        if os.path.exists(path):
            index["files"].append({
                "path": f"mods/{name}",
                "hashes": {"sha1": sha(path, "sha1"), "sha512": sha(path, "sha512")},
                "env": {"client": "optional", "server": "unsupported"},
                "downloads": [url],
                "fileSize": os.path.getsize(path),
            })
    for name, url in SHARED_MODS.items():
        path = os.path.join(visual, name)
        if os.path.exists(path):
            index["files"].append({
                "path": f"mods/{name}",
                "hashes": {"sha1": sha(path, "sha1"), "sha512": sha(path, "sha512")},
                "env": {"client": "optional", "server": "optional"},
                "downloads": [url],
                "fileSize": os.path.getsize(path),
            })
    shader = os.path.join(visual, SHADER_PACK[0])
    if os.path.exists(shader):
        index["files"].append({
            "path": f"shaderpacks/{SHADER_PACK[0]}",
            "hashes": {"sha1": sha(shader, "sha1"), "sha512": sha(shader, "sha512")},
            "env": {"client": "optional", "server": "unsupported"},
            "downloads": [SHADER_PACK[1]],
            "fileSize": os.path.getsize(shader),
        })
    mrpack = os.path.join(DIST, f"FreshTracks-{v}.mrpack")
    with zipfile.ZipFile(mrpack, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2))
        z.write(jar, f"overrides/mods/freshtracks-{v}.jar")
        z.writestr("overrides/config/iris.properties", IRIS_PROPERTIES)

    # 3. server pack
    server = os.path.join(DIST, f"FreshTracks-{v}-server.zip")
    fmt = {"mc": MINECRAFT, "loader": LOADER, "installer": INSTALLER, "v": v}
    with zipfile.ZipFile(server, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(jar, f"FreshTracks-server/mods/freshtracks-{v}.jar")
        z.write(api, f"FreshTracks-server/mods/fabric-api-{FABRIC_API}.jar")
        z.writestr("FreshTracks-server/start.bat", START_BAT.format(**fmt).replace("\n", "\r\n"))
        info = zipfile.ZipInfo("FreshTracks-server/start.sh")
        info.external_attr = 0o755 << 16
        z.writestr(info, START_SH.format(**fmt))
        z.writestr("FreshTracks-server/README.txt", SERVER_README.format(**fmt))

    for name in sorted(os.listdir(DIST)):
        print(f"{os.path.getsize(os.path.join(DIST, name)):>10,}  dist/{name}")


if __name__ == "__main__":
    main()
