"""Fixtures for the Remote Screens integration's tests."""

from __future__ import annotations

from collections.abc import Generator
from copy import deepcopy
from unittest.mock import AsyncMock, patch

import pytest

from pytest_homeassistant_custom_component.common import MockConfigEntry

from custom_components.remote_screens.const import CONF_API_KEY, DOMAIN

SCREENS = [
    {
        "id": "demo",
        "name": "Living room",
        "settings": {
            "brightness": 80,
            "darkMode": False,
            "standbyMinutes": 10,
            "standbyBrightness": 0,
            "nightBrightness": 5,
            "nightDark": True,
            "backToHomeSeconds": 0,
            "swipe": True,
            "orientation": 0,
        },
        "pages": ["Home", "Rooms", "More"],
        "players": [
            {
                "deviceId": "tablet-1",
                "name": "Pixel Tablet",
                "screenId": "demo",
                "online": True,
                "widthDp": 1280,
                "heightDp": 800,
                "appVersion": 1,
                "lastSeen": 0,
                "awake": True,
                "page": 1,
                "battery": 90,
            }
        ],
    }
]

PLAYERS = [
    {
        **SCREENS[0]["players"][0],
        "kiosk": "locked",
        "charging": True,
        "telemetry": {
            "hardware": ["light", "proximity", "accelerometer"],
            "battery": {"health": "good", "temperatureC": 31.5, "voltageMv": 4200, "currentMa": 1000},
            "network": {"ssid": "HomeNet", "rssiDbm": -54},
            "system": {
                "storageFreeBytes": 11_200_000_000,
                "osVersion": "14",
                "appVersionName": "1.4.0",
                "manufacturer": "Google",
                "model": "Pixel Tablet",
            },
            "environment": {"illuminanceLux": 182.0, "isNear": False},
            "motion": {"isMoving": True, "orientation": "landscape", "lastMotionAt": 1_700_000_000_000},
        },
        "telemetryAt": 1_700_000_000_000,
    },
    {
        "deviceId": "spare",
        "name": "Spare tablet",
        "screenId": None,
        "online": False,
        "widthDp": 800,
        "heightDp": 480,
        "appVersion": 1,
        "lastSeen": 0,
        "awake": False,
        "page": 0,
    },
]

ENTRY_DATA = {"host": "127.0.0.1", "port": 8100, CONF_API_KEY: "key"}


@pytest.fixture(autouse=True)
def auto_enable_custom_integrations(enable_custom_integrations):
    yield


@pytest.fixture
def client() -> Generator[AsyncMock]:
    """The add-on's API, answering with one screen."""
    with (
        patch("custom_components.remote_screens.RemoteScreensClient", autospec=True) as cls,
        patch("custom_components.remote_screens.config_flow.RemoteScreensClient", new=cls),
    ):
        c = cls.return_value
        screens = deepcopy(SCREENS)
        c.players_data = deepcopy(PLAYERS)

        async def get():
            return deepcopy(screens)

        async def get_players():
            return deepcopy(c.players_data)

        async def set_settings(screen_id, patch):
            next(s for s in screens if s["id"] == screen_id)["settings"].update(patch)

        c.screens.side_effect = get
        c.players.side_effect = get_players
        c.set_settings.side_effect = set_settings
        yield c


@pytest.fixture
def entry() -> MockConfigEntry:
    return MockConfigEntry(domain=DOMAIN, unique_id=DOMAIN, data=ENTRY_DATA, title="Remote Screens")
