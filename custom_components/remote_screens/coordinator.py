"""Polls the add-on for its screens and tablets."""

from __future__ import annotations

import logging
from typing import Any

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import ConfigEntryAuthFailed, HomeAssistantError
from homeassistant.helpers.update_coordinator import DataUpdateCoordinator, UpdateFailed

from .api import RemoteScreensAuthError, RemoteScreensClient, RemoteScreensError, RemoteScreensNotFoundError
from .const import DOMAIN, SCAN_INTERVAL

_LOGGER = logging.getLogger(__name__)

type RemoteScreensConfigEntry = ConfigEntry[RemoteScreensCoordinator]


class RemoteScreensCoordinator(DataUpdateCoordinator[dict[str, dict[str, Any]]]):
    """The add-on's screens by id, and its tablets by device id in [players]."""

    config_entry: RemoteScreensConfigEntry

    def __init__(self, hass: HomeAssistant, entry: RemoteScreensConfigEntry, client: RemoteScreensClient) -> None:
        super().__init__(hass, _LOGGER, config_entry=entry, name=DOMAIN, update_interval=SCAN_INTERVAL)
        self.client = client
        self.players: dict[str, dict[str, Any]] = {}

    async def _async_update_data(self) -> dict[str, dict[str, Any]]:
        try:
            screens = await self.client.screens()
            try:
                players = await self.client.players()
            except RemoteScreensNotFoundError:
                # An add-on from before tablet sensors: only the tablets its screens list, without sensors.
                players = [p for s in screens for p in s.get("players") or []]
        except RemoteScreensAuthError as err:
            raise ConfigEntryAuthFailed(str(err)) from err
        except RemoteScreensError as err:
            raise UpdateFailed(str(err)) from err
        self.players = {p["deviceId"]: p for p in players}
        return {s["id"]: s for s in screens}

    async def async_set_settings(self, screen_id: str, patch: dict[str, Any]) -> None:
        """Sends a settings change and shows it at once, before the next poll confirms it."""
        try:
            await self.client.set_settings(screen_id, patch)
        except RemoteScreensError as err:
            raise HomeAssistantError(str(err)) from err
        screen = self.data.get(screen_id)
        if screen is not None:
            data = dict(self.data)
            data[screen_id] = {**screen, "settings": {**screen["settings"], **patch}}
            self.async_set_updated_data(data)
        await self.async_request_refresh()
