"""Constants for the Remote Screens integration."""

from __future__ import annotations

from datetime import timedelta
from typing import Final

DOMAIN: Final = "remote_screens"
MANUFACTURER: Final = "Remote Screens"

CONF_API_KEY: Final = "api_key"
DEFAULT_HOST: Final = "127.0.0.1"
DEFAULT_PORT: Final = 8100
INTERCOM_PORT: Final = 8766

SCAN_INTERVAL: Final = timedelta(seconds=30)
# The add-on fires this when a tablet connects, disconnects, or changes awake, page, kiosk or charging.
EVENT_PLAYERS_CHANGED: Final = "remote_screens_players_changed"

SERVICE_SHOW_ALERT: Final = "show_alert"
SERVICE_OPEN_PAGE: Final = "open_page"
SERVICE_WAKE: Final = "wake"
SERVICE_CLOSE: Final = "close"
SERVICE_PANEL_TAP: Final = "panel_tap"

ATTR_SCREENS: Final = "screens"
ATTR_TITLE: Final = "title"
ATTR_MESSAGE: Final = "message"
ATTR_ICON: Final = "icon"
ATTR_LEVEL: Final = "level"
ATTR_CAMERA: Final = "camera"
ATTR_BUTTONS: Final = "buttons"
ATTR_TIMEOUT: Final = "timeout"
ATTR_TAG: Final = "tag"
ATTR_PAGE: Final = "page"

LEVELS: Final = ["info", "warning", "danger"]
