"""Where each tablet is, for the tablets whose screen shares their location."""

from __future__ import annotations

from typing import Any

from homeassistant.components.device_tracker import SourceType, TrackerEntity
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .coordinator import RemoteScreensConfigEntry
from .entity import RemoteScreensTabletEntity


async def async_setup_entry(
    hass: HomeAssistant, entry: RemoteScreensConfigEntry, async_add_entities: AddConfigEntryEntitiesCallback
) -> None:
    coordinator = entry.runtime_data
    known: set[str] = set()

    def add_new() -> None:
        """A tracker appears once a tablet first reports a fix, so tablets that never share get none."""
        new = [
            device_id
            for device_id, player in coordinator.players.items()
            if device_id not in known and (player.get("telemetry") or {}).get("location")
        ]
        known.update(new)
        async_add_entities(TabletTracker(coordinator, device_id) for device_id in new)

    add_new()
    entry.async_on_unload(coordinator.async_add_listener(add_new))


class TabletTracker(RemoteScreensTabletEntity, TrackerEntity):
    _attr_source_type = SourceType.GPS

    def __init__(self, coordinator, device_id: str) -> None:
        super().__init__(coordinator, device_id, "location")

    @property
    def fix(self) -> dict[str, Any]:
        return self.telemetry.get("location") or {}

    @property
    def available(self) -> bool:
        return super().available and bool(self.fix)

    @property
    def latitude(self) -> float | None:
        return self.fix.get("latitude")

    @property
    def longitude(self) -> float | None:
        return self.fix.get("longitude")

    @property
    def location_accuracy(self) -> float:
        return self.fix.get("accuracyM") or 0

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        altitude = self.fix.get("altitudeM")
        return {"altitude": altitude} if altitude is not None else {}
