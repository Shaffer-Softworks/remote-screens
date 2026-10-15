"""The PIN that unlocks a screen's kiosk on the tablet."""

from __future__ import annotations

from homeassistant.components.text import TextEntity, TextMode
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
        async_add_entities(KioskPin(coordinator, s) for s in new)

    add_new()
    entry.async_on_unload(coordinator.async_add_listener(add_new))


class KioskPin(RemoteScreensEntity, TextEntity):
    """Four to eight digits; empty leaves unlocking to Home Assistant alone."""

    _attr_entity_category = EntityCategory.CONFIG
    _attr_mode = TextMode.PASSWORD
    _attr_native_min = 0
    _attr_native_max = 8
    _attr_pattern = r"^(\d{4,8})?$"

    def __init__(self, coordinator, screen_id: str) -> None:
        super().__init__(coordinator, screen_id, "kiosk_pin")

    @property
    def native_value(self) -> str | None:
        return self.settings.get("kioskPin") or ""

    async def async_set_value(self, value: str) -> None:
        await self.coordinator.async_set_settings(self.screen_id, {"kioskPin": value or None})
