#!/usr/bin/env python3
"""Temporarily append docs-capture panel pages to the demo screen for screenshots."""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:18099"

# Mirrors DemoLayout.docsCapturePages() / helper page ids.
PANEL_PAGES = {
    "lights": {
        "id": "lights",
        "title": "Lights",
        "icon": "lightbulb-group",
        "tiles": [{"id": "li1", "kind": "lights", "full": True}],
    },
    "cameras": {
        "id": "cameras",
        "title": "Cameras",
        "icon": "cctv",
        "tiles": [
            {
                "id": "cm1",
                "kind": "cameras",
                "full": True,
                "options": {
                    "cameras": "camera.front_door=Front Door;camera.driveway",
                },
            }
        ],
    },
    "blinds": {
        "id": "blinds",
        "title": "Blinds",
        "icon": "blinds",
        "tiles": [{"id": "bl1", "kind": "blinds", "full": True}],
    },
    "security": {
        "id": "security",
        "title": "Security",
        "icon": "shield-home",
        "tiles": [{"id": "se1", "kind": "security", "full": True}],
    },
    "vacuum": {
        "id": "vacuum",
        "title": "Vacuum",
        "icon": "robot-vacuum",
        "tiles": [
            {
                "id": "va1",
                "entityId": "vacuum.robot",
                "kind": "vacuum",
                "full": True,
            }
        ],
    },
    "energy_dashboard": {
        "id": "energy_dashboard",
        "title": "Energy",
        "icon": "chart-box",
        "tiles": [{"id": "ed1", "kind": "energy_dashboard", "full": True}],
    },
}


def get_json(path: str) -> object:
    with urllib.request.urlopen(f"{BASE}{path}") as response:
        return json.load(response)


def put_screen(screen: dict) -> None:
    body = json.dumps(screen).encode()
    request = urllib.request.Request(
        f"{BASE}/api/screens/{screen['id']}",
        data=body,
        method="PUT",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request) as response:
        response.read()


def main() -> int:
    global BASE
    parser = argparse.ArgumentParser()
    parser.add_argument("--ingress", default=BASE)
    parser.add_argument(
        "mode",
        choices=("reset", "panels", "one", "append"),
        help=(
            "reset=small player-safe core; panels=Home+docs panels; "
            "one=<panel>; append=keep current pages and add missing docs panels (editor)"
        ),
    )
    parser.add_argument("panel", nargs="?", choices=sorted(PANEL_PAGES))
    args = parser.parse_args()
    BASE = args.ingress.rstrip("/")

    layout = get_json("/api/layout")
    screen = next(item for item in layout["screens"] if item["id"] == "demo")
    # Prefer Home only as the stable base — a full demo document can trip Remote Compose paint CME.
    home = next(page for page in screen["pages"] if page["id"] == "home")

    if args.mode == "reset":
        # Restore a small navigable core when those pages still exist on the screen.
        core_ids = ["home", "living", "rooms", "weather", "thermostat"]
        by_id = {page["id"]: page for page in screen["pages"]}
        screen["pages"] = [by_id[page_id] for page_id in core_ids if page_id in by_id] or [home]
    elif args.mode == "panels":
        screen["pages"] = [home] + list(PANEL_PAGES.values())
    elif args.mode == "append":
        have = {page["id"] for page in screen["pages"]}
        screen["pages"] = screen["pages"] + [
            page for page_id, page in PANEL_PAGES.items() if page_id not in have
        ]
    else:
        if not args.panel:
            print("one requires a panel id", file=sys.stderr)
            return 2
        screen["pages"] = [home, PANEL_PAGES[args.panel]]

    put_screen(screen)
    print("pages:", [page["title"] for page in screen["pages"]])
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except urllib.error.URLError as error:
        print(f"layout API failed: {error}", file=sys.stderr)
        raise SystemExit(1)
