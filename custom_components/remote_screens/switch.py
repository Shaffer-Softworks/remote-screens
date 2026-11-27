"""Dark mode, night, standby screen-off, wake-on-proximity/face, Assist, the kiosk lock, and tablet sensors."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from homeassistant.components.switch import SwitchEntity, SwitchEntityDescription
from homeassistant.const import EntityCategory
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .coordinator import RemoteScreensConfigEntry
from .entity import RemoteScreensEntity


@dataclass(frozen=True, kw_only=True)
class SettingSwitchDescription(SwitchEntityDescription):
    setting: str
    #: When set, the boolean lives at settings[nested][setting] and a patch merges that object.
    nested: str | None = None


SWITCHES: tuple[SettingSwitchDescription, ...] = (
    SettingSwitchDescription(key="dark_mode", setting="darkMode"),
    SettingSwitchDescription(key="night_dark", setting="nightDark", entity_category=EntityCategory.CONFIG),
    SettingSwitchDescription(key="kiosk", setting="kiosk", entity_category=EntityCategory.CONFIG),
    SettingSwitchDescription(
        key="standby_screen_off", setting="standbyScreenOff", entity_category=EntityCategory.CONFIG,
    ),
    SettingSwitchDescription(
        key="wake_on_proximity", setting="wakeOnProximity", entity_category=EntityCategory.CONFIG,
    ),
    SettingSwitchDescription(
        key="wake_on_face", setting="wakeOnFace", entity_category=EntityCategory.CONFIG,
    ),
    SettingSwitchDescription(
        key="assist", setting="assist", entity_category=EntityCategory.CONFIG,
    ),
    SettingSwitchDescription(
        key="report_telemetry", setting="reportTelemetry", entity_category=EntityCategory.CONFIG,
    ),
    SettingSwitchDescription(
        key="share_location", setting="shareLocation", entity_category=EntityCategory.CONFIG,
    ),
    SettingSwitchDescription(
        key="screensaver",
        setting="enabled",
        nested="screensaver",
        entity_category=EntityCategory.CONFIG,
    ),
)


async def async_setup_entry(
    hass: HomeAssistant, entry: RemoteScreensConfigEntry, async_add_entities: AddConfigEntryEntitiesCallback
) -> None:
    coordinator = entry.runtime_data
    known: set[str] = set()

    def add_new() -> None:
        new = [s for s in coordinator.data if s not in known]
        known.update(new)
        async_add_entities(SettingSwitch(coordinator, s, d) for s in new for d in SWITCHES)

    add_new()
    entry.async_on_unload(coordinator.async_add_listener(add_new))


class SettingSwitch(RemoteScreensEntity, SwitchEntity):
    entity_description: SettingSwitchDescription

    def __init__(self, coordinator, screen_id: str, description: SettingSwitchDescription) -> None:
        super().__init__(coordinator, screen_id, description.key)
        self.entity_description = description

    @property
    def is_on(self) -> bool | None:
        settings = self._setting_group()
        return settings.get(self.entity_description.setting)

    def _setting_group(self) -> dict[str, Any]:
        nested = self.entity_description.nested
        if nested is None:
            return self.settings
        group = self.settings.get(nested)
        return group if isinstance(group, dict) else {}

    def _patch(self, value: bool) -> dict[str, Any]:
        patch: dict[str, Any] = {self.entity_description.setting: value}
        nested = self.entity_description.nested
        if nested is None:
            return patch
        return {nested: patch}

    async def async_turn_on(self, **kwargs: Any) -> None:
        await self.coordinator.async_set_settings(self.screen_id, self._patch(True))

    async def async_turn_off(self, **kwargs: Any) -> None:
        await self.coordinator.async_set_settings(self.screen_id, self._patch(False))
