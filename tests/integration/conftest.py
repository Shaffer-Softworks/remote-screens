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

        async def get():
            return deepcopy(screens)

        async def set_settings(screen_id, patch):
            next(s for s in screens if s["id"] == screen_id)["settings"].update(patch)

        c.screens.side_effect = get
        c.set_settings.side_effect = set_settings
        yield c


@pytest.fixture
def entry() -> MockConfigEntry:
    return MockConfigEntry(domain=DOMAIN, unique_id=DOMAIN, data=ENTRY_DATA, title="Remote Screens")
