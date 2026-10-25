"""The devices and entities: one device per screen, and one per tablet."""

from __future__ import annotations

from typing import Any

from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from .const import DOMAIN, MANUFACTURER
from .coordinator import RemoteScreensCoordinator


def tablet_identifier(device_id: str) -> tuple[str, str]:
    """A tablet's device identifier; kept apart from screen ids, which share the domain."""
    return DOMAIN, f"player_{device_id}"


def telemetry_of(player: dict[str, Any]) -> dict[str, Any]:
    return player.get("telemetry") or {}


def has_hardware(player: dict[str, Any], *hardware: str) -> bool:
    """Whether the tablet reported any of [hardware]; no requirement is always true."""
    reported = telemetry_of(player).get("hardware") or []
    return not hardware or any(h in reported for h in hardware)


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


class RemoteScreensTabletEntity(CoordinatorEntity[RemoteScreensCoordinator]):
    """An entity of one tablet, from what it reports about itself. Shown under the screen it plays."""

    _attr_has_entity_name = True

    #: Stays available while the tablet is offline (only the connectivity sensor).
    available_offline = False

    def __init__(self, coordinator: RemoteScreensCoordinator, device_id: str, key: str) -> None:
        super().__init__(coordinator)
        self.device_id = device_id
        self._attr_unique_id = f"player_{device_id}_{key}"
        self._attr_translation_key = key
        player = self.player
        system = self.telemetry.get("system") or {}
        screen_id = player.get("screenId")
        self._attr_device_info = DeviceInfo(
            identifiers={tablet_identifier(device_id)},
            name=player.get("name") or device_id,
            manufacturer=system.get("manufacturer") or MANUFACTURER,
            model=system.get("model") or "Tablet",
            sw_version=system.get("appVersionName"),
            via_device=(DOMAIN, screen_id) if screen_id in coordinator.data else None,
        )

    @property
    def player(self) -> dict[str, Any]:
        return self.coordinator.players.get(self.device_id) or {}

    @property
    def telemetry(self) -> dict[str, Any]:
        return telemetry_of(self.player)

    @property
    def available(self) -> bool:
        if not super().available or self.device_id not in self.coordinator.players:
            return False
        return self.available_offline or bool(self.player.get("online"))
