"""The hours of night mode."""

from __future__ import annotations

from datetime import time

from homeassistant.components.time import TimeEntity
from homeassistant.const import EntityCategory
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .coordinator import RemoteScreensConfigEntry
from .entity import RemoteScreensEntity

START = ("night_start", "nightStart", time(22, 0))
END = ("night_end", "nightEnd", time(7, 0))


async def async_setup_entry(
    hass: HomeAssistant, entry: RemoteScreensConfigEntry, async_add_entities: AddConfigEntryEntitiesCallback
) -> None:
    coordinator = entry.runtime_data
    known: set[str] = set()

    def add_new() -> None:
        new = [s for s in coordinator.data if s not in known]
        known.update(new)
        async_add_entities(
            entity for s in new for entity in (NightTime(coordinator, s, START, END), NightTime(coordinator, s, END, START))
        )

    add_new()
    entry.async_on_unload(coordinator.async_add_listener(add_new))


def parse(value: str | None) -> time | None:
    if not value:
        return None
    try:
        return time.fromisoformat(value)
    except ValueError:
        return None


class NightTime(RemoteScreensEntity, TimeEntity):
    """One end of night mode. The add-on ignores one end without the other, so setting it fills in the other."""

    _attr_entity_category = EntityCategory.CONFIG

    def __init__(self, coordinator, screen_id: str, this: tuple[str, str, time], other: tuple[str, str, time]) -> None:
        super().__init__(coordinator, screen_id, this[0])
        self._setting = this[1]
        _, self._other, self._other_default = other

    @property
    def native_value(self) -> time | None:
        return parse(self.settings.get(self._setting))

    async def async_set_value(self, value: time) -> None:
        patch = {self._setting: value.strftime("%H:%M")}
        if not self.settings.get(self._other):
            patch[self._other] = self._other_default.strftime("%H:%M")
        await self.coordinator.async_set_settings(self.screen_id, patch)
