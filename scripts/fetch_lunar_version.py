"""
Fetches the current Lunar Client version and git commit from the launcher API.

Extracts lunarBuildData.txt from lunar.jar using HTTP range requests
(only downloads ~2.5MB of the ZIP central directory, not the full 56MB JAR).

Outputs GitHub Actions-compatible key=value lines to stdout.
"""

import io
import json
import struct
import subprocess
import sys
import urllib.request
import zlib

LAUNCH_API = "https://api.lunarclientprod.com/launcher/launch"
USER_AGENT = "LunarClient/3.3.3"


def main():
    jar_url = get_jar_url()
    jar_size = get_content_length(jar_url)
    build_data = extract_build_data(jar_url, jar_size)
    if "lunarVersion" in build_data:
        print(f"lunarVersion={build_data['lunarVersion']}")
    if "fullGitHash" in build_data:
        print(f"lunarGitCommit={build_data['fullGitHash']}")


def get_jar_url():
    body = json.dumps({
        "hwid": "0", "hwid_private": "0", "os": "linux", "arch": "x64",
        "launcher_version": "3.3.3", "version": "1.8.9", "branch": "master",
        "launch_type": "OFFLINE", "module": "lunar",
        "installation_id": "00000000-0000-0000-0000-000000000000",
        "os_release": "6.1.0",
    }).encode()
    req = urllib.request.Request(LAUNCH_API, data=body, headers={
        "Content-Type": "application/json",
        "User-Agent": USER_AGENT,
    })
    resp = json.load(urllib.request.urlopen(req, timeout=15))
    for art in resp["launchTypeData"]["artifacts"]:
        if art["name"] == "lunar.jar":
            return art["url"]
    raise RuntimeError("lunar.jar not found in launch API response")


def get_content_length(url):
    output = subprocess.check_output(
        ["curl", "-sI", "-H", f"User-Agent: {USER_AGENT}", url],
    ).decode()
    for line in output.splitlines():
        if line.lower().startswith("content-length:"):
            return int(line.split(":", 1)[1].strip())
    raise RuntimeError("Could not determine content length")


def fetch_range(url, start, end):
    return subprocess.check_output(
        ["curl", "-sf", url, "-r", f"{start}-{end}", "-H", f"User-Agent: {USER_AGENT}"],
    )


def extract_build_data(url, total_size):
    # Read the last 64KB to find the End of Central Directory.
    tail = fetch_range(url, total_size - 65536, total_size - 1)
    eocd_pos = find_eocd(tail)
    cd_size = struct.unpack_from("<I", tail, eocd_pos + 12)[0]
    cd_offset = struct.unpack_from("<I", tail, eocd_pos + 16)[0]

    # Read the central directory and find lunarBuildData.txt.
    cd = fetch_range(url, cd_offset, cd_offset + cd_size - 1)
    buf = io.BytesIO(cd)
    while True:
        sig = buf.read(4)
        if sig != b"\x50\x4b\x01\x02":
            break
        fields = buf.read(42)
        method = struct.unpack_from("<H", fields, 6)[0]
        comp_size = struct.unpack_from("<I", fields, 16)[0]
        name_len = struct.unpack_from("<H", fields, 24)[0]
        extra_len = struct.unpack_from("<H", fields, 26)[0]
        comment_len = struct.unpack_from("<H", fields, 28)[0]
        local_offset = struct.unpack_from("<I", fields, 38)[0]
        name = buf.read(name_len).decode()
        buf.read(extra_len + comment_len)
        if name == "lunarBuildData.txt":
            return read_entry(url, local_offset, comp_size, method)

    raise RuntimeError("lunarBuildData.txt not found in lunar.jar")


def read_entry(url, offset, comp_size, method):
    local = fetch_range(url, offset, offset + 30 + 256 + comp_size)
    name_len = struct.unpack_from("<H", local, 26)[0]
    extra_len = struct.unpack_from("<H", local, 28)[0]
    start = 30 + name_len + extra_len
    raw = local[start : start + comp_size]
    if method == 8:
        raw = zlib.decompress(raw, -15)
    result = {}
    for line in raw.decode().splitlines():
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            result[k.strip()] = v.strip()
    return result


def find_eocd(data):
    for i in range(len(data) - 22, -1, -1):
        if data[i : i + 4] == b"\x50\x4b\x05\x06":
            return i
    raise RuntimeError("ZIP EOCD not found")


if __name__ == "__main__":
    main()
