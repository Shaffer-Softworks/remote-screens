"""The device and entity each screen gets."""

from __future__ import annotations

from typing import Any

from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from .const import DOMAIN, MANUFACTURER
from .coordinator import RemoteScreensCoordinator


class RemoteScreensEntity(CoordinatorEntity[RemoteScreensCoordinator]):
    """An entity of one screen; the screen is the device, whatever tablets show it."""

    _attr_has_entity_name = True

    def __init__(self, coordinator: RemoteScreensCoordinator, screen_id: str, key: str) -> None:
        super().__init__(coordinator)
        self.screen_id = screen_id
        self._attr_unique_id = f"{screen_id}_{key}"
        self._attr_translation_key = key
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, screen_id)},
            name=self.screen["name"],
            manufacturer=MANUFACTURER,
            model="Screen",
        )

    @property
    def screen(self) -> dict[str, Any]:
        return self.coordinator.data.get(self.screen_id) or {}

    @property
    def settings(self) -> dict[str, Any]:
        return self.screen.get("settings") or {}

    @property
    def available(self) -> bool:
        return super().available and self.screen_id in self.coordinator.data
