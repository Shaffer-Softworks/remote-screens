"""Whether a tablet is showing the screen, and each tablet's own state."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from homeassistant.components.binary_sensor import (
    BinarySensorDeviceClass,
    BinarySensorEntity,
    BinarySensorEntityDescription,
)
from homeassistant.const import EntityCategory
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .coordinator import RemoteScreensConfigEntry
from .entity import RemoteScreensEntity, RemoteScreensTabletEntity, has_hardware, telemetry_of


@dataclass(frozen=True, kw_only=True)
class TabletBinarySensorDescription(BinarySensorEntityDescription):
    value_fn: Callable[[dict[str, Any]], bool | None]
    #: Only created when the tablet reports one of these in `hardware`.
    hardware: tuple[str, ...] = ()
    needs_telemetry: bool = True


def group(player: dict[str, Any], name: str) -> dict[str, Any]:
    return telemetry_of(player).get(name) or {}


TABLET_SENSORS: tuple[TabletBinarySensorDescription, ...] = (
    TabletBinarySensorDescription(
        key="online", device_class=BinarySensorDeviceClass.CONNECTIVITY, entity_category=EntityCategory.DIAGNOSTIC,
        needs_telemetry=False, value_fn=lambda p: bool(p.get("online")),
    ),
    TabletBinarySensorDescription(
        key="charging", device_class=BinarySensorDeviceClass.BATTERY_CHARGING, needs_telemetry=False,
        value_fn=lambda p: p.get("charging"),
    ),
    TabletBinarySensorDescription(
        key="awake", entity_category=EntityCategory.DIAGNOSTIC, needs_telemetry=False,
        value_fn=lambda p: p.get("awake"),
    ),
    TabletBinarySensorDescription(
        key="moving", device_class=BinarySensorDeviceClass.MOVING, hardware=("accelerometer", "significant_motion"),
        value_fn=lambda p: group(p, "motion").get("isMoving"),
    ),
    TabletBinarySensorDescription(
        key="proximity", device_class=BinarySensorDeviceClass.OCCUPANCY, hardware=("proximity",),
        value_fn=lambda p: group(p, "environment").get("isNear"),
    ),
)


async def async_setup_entry(
    hass: HomeAssistant, entry: RemoteScreensConfigEntry, async_add_entities: AddConfigEntryEntitiesCallback
) -> None:
    coordinator = entry.runtime_data
    known_screens: set[str] = set()
    known_tablets: set[tuple[str, str]] = set()

    def add_new() -> None:
        new_screens = [s for s in coordinator.data if s not in known_screens]
        known_screens.update(new_screens)
        entities: list[BinarySensorEntity] = [Connected(coordinator, s) for s in new_screens]
        for device_id, player in coordinator.players.items():
            has_telemetry = bool(player.get("telemetry"))
            for description in TABLET_SENSORS:
                if (device_id, description.key) in known_tablets:
                    continue
                if description.needs_telemetry and not has_telemetry:
                    continue
                if not has_hardware(player, *description.hardware):
                    continue
                known_tablets.add((device_id, description.key))
                entities.append(TabletBinarySensor(coordinator, device_id, description))
        async_add_entities(entities)

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


class TabletBinarySensor(RemoteScreensTabletEntity, BinarySensorEntity):
    entity_description: TabletBinarySensorDescription

    def __init__(self, coordinator, device_id: str, description: TabletBinarySensorDescription) -> None:
        super().__init__(coordinator, device_id, description.key)
        self.entity_description = description
        self.available_offline = description.key == "online"

    @property
    def is_on(self) -> bool | None:
        return self.entity_description.value_fn(self.player)
