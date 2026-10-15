"""Whether a tablet is showing the screen."""

from __future__ import annotations

from typing import Any

from homeassistant.components.binary_sensor import BinarySensorDeviceClass, BinarySensorEntity
from homeassistant.const import EntityCategory
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .coordinator import RemoteScreensConfigEntry
from .entity import RemoteScreensEntity


async def async_setup_entry(
    hass: HomeAssistant, entry: RemoteScreensConfigEntry, async_add_entities: AddConfigEntryEntitiesCallback
) -> None:
    coordinator = entry.runtime_data
    known: set[str] = set()

    def add_new() -> None:
        new = [s for s in coordinator.data if s not in known]
        known.update(new)
        async_add_entities(Connected(coordinator, s) for s in new)

    add_new()
    entry.async_on_unload(coordinator.async_add_listener(add_new))


class Connected(RemoteScreensEntity, BinarySensorEntity):
    _attr_device_class = BinarySensorDeviceClass.CONNECTIVITY
    _attr_entity_category = EntityCategory.DIAGNOSTIC

    def __init__(self, coordinator, screen_id: str) -> None:
        super().__init__(coordinator, screen_id, "connected")

    @property
    def is_on(self) -> bool:
        return any(p.get("online") for p in self.screen.get("players") or [])

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        return {
            "players": [
                {k: p.get(k) for k in ("name", "online", "awake", "battery", "kiosk", "widthDp", "heightDp", "appVersion")}
                for p in self.screen.get("players") or []
            ]
        }
