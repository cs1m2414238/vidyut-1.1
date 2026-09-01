"""Generate nationwide demo charger locations from Survey of India districts.

One representative point is selected inside each official district polygon and
optionally snapped to the nearest road in the local OSRM graph. The resulting
coordinates describe synthetic demo coverage, not verified charger locations.

Requires: pyshp, shapely, pyproj
"""

from __future__ import annotations

import argparse
import json
import urllib.parse
import urllib.request
from pathlib import Path

import shapefile
from pyproj import CRS, Transformer
from shapely.geometry import shape


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("shapefile", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--osrm", default="http://localhost:5000")
    return parser.parse_args()


def title_name(value: str) -> str:
    words = value.strip().title()
    replacements = {
        "And ": "and ",
        " Of ": " of ",
        "The ": "the ",
        "Dadra & Nagar Haveli & Daman & Diu": "Dadra and Nagar Haveli and Daman and Diu",
        "Jammu & Kashmir": "Jammu and Kashmir",
        "Andaman & Nicobar Island": "Andaman and Nicobar Islands",
    }
    for source, target in replacements.items():
        words = words.replace(source, target)
    return words[0].upper() + words[1:] if words else words


def nearest_road(osrm: str, longitude: float, latitude: float) -> tuple[float, float, bool]:
    coordinate = f"{longitude:.7f},{latitude:.7f}"
    url = f"{osrm.rstrip('/')}/nearest/v1/driving/{urllib.parse.quote(coordinate)}?number=1"
    with urllib.request.urlopen(url, timeout=8) as response:
        payload = json.load(response)
    if payload.get("code") != "Ok" or not payload.get("waypoints"):
        return longitude, latitude, False
    waypoint = payload["waypoints"][0]
    # A partial local OSRM graph can otherwise pull an uncovered district
    # hundreds of kilometres into the covered region.
    if float(waypoint.get("distance") or 0) > 25_000:
        return longitude, latitude, False
    snapped = waypoint["location"]
    return float(snapped[0]), float(snapped[1]), True


def main() -> None:
    args = parse_args()
    reader = shapefile.Reader(str(args.shapefile), encoding="utf-8")
    source_crs = CRS.from_wkt(args.shapefile.with_suffix(".prj").read_text(encoding="utf-8"))
    transformer = Transformer.from_crs(source_crs, CRS.from_epsg(4326), always_xy=True)
    districts: list[dict[str, object]] = []
    snap_failures = 0
    snapped_to_roads = 0
    skipped_without_lgd_code = 0

    for item in reader.iterShapeRecords():
        record = item.record.as_dict()
        state_code = str(record["STATE_LGD"]).strip()
        district_code = str(record["DIST_LGD"]).strip()
        state_name = str(record["STATE_UT"]).strip()
        district_name = str(record["DISTRICT"]).strip()
        if not state_code.isdigit() or not district_code.isdigit() or not state_name or not district_name:
            skipped_without_lgd_code += 1
            continue
        representative = shape(item.shape.__geo_interface__).representative_point()
        longitude, latitude = transformer.transform(representative.x, representative.y)
        try:
            longitude, latitude, snapped = nearest_road(args.osrm, longitude, latitude)
            snapped_to_roads += int(snapped)
        except Exception:
            snap_failures += 1

        district = {
            "key": f"SOI-{state_code.zfill(2)}-{district_code}",
            "state": title_name(state_name),
            "district": title_name(district_name),
            "latitude": round(latitude, 6),
            "longitude": round(longitude, 6),
        }
        if not 6 <= district["latitude"] <= 38 or not 68 <= district["longitude"] <= 98:
            raise ValueError(f"Coordinate outside India bounds: {district}")
        districts.append(district)

    districts.sort(key=lambda row: (str(row["state"]), str(row["district"]), str(row["key"])))
    keys = {str(row["key"]) for row in districts}
    names = {(str(row["state"]), str(row["district"])) for row in districts}
    if len(keys) != len(districts) or len(names) != len(districts):
        raise ValueError("The Survey of India district dataset contains duplicate keys or names")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(districts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"Generated {len(districts)} LGD-coded districts; "
        f"skipped without LGD code: {skipped_without_lgd_code}; "
        f"snapped to local roads: {snapped_to_roads}; "
        f"OSRM request fallbacks: {snap_failures}"
    )


if __name__ == "__main__":
    main()
