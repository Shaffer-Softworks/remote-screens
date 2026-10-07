"""Brightness, standby and back-to-home settings."""

from __future__ import annotations

from dataclasses import dataclass

from homeassistant.components.number import NumberEntity, NumberEntityDescription, NumberMode
from homeassistant.const import PERCENTAGE, EntityCategory, UnitOfTime
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .coordinator import RemoteScreensConfigEntry
from .entity import RemoteScreensEntity


@dataclass(frozen=True, kw_only=True)
class SettingNumberDescription(NumberEntityDescription):
    """A number setting, `setting` being its name in the add-on's screen settings."""

    setting: str


NUMBERS: tuple[SettingNumberDescription, ...] = (
    SettingNumberDescription(
        key="brightness", setting="brightness", native_min_value=1, native_max_value=100, native_step=1,
        native_unit_of_measurement=PERCENTAGE, mode=NumberMode.SLIDER,
    ),
    SettingNumberDescription(
        key="standby_minutes", setting="standbyMinutes", native_min_value=0, native_max_value=240, native_step=1,
        native_unit_of_measurement=UnitOfTime.MINUTES, mode=NumberMode.BOX, entity_category=EntityCategory.CONFIG,
    ),
    SettingNumberDescription(
        key="standby_brightness", setting="standbyBrightness", native_min_value=0, native_max_value=100,
        native_step=1, native_unit_of_measurement=PERCENTAGE, mode=NumberMode.SLIDER,
        entity_category=EntityCategory.CONFIG,
    ),
    SettingNumberDescription(
        key="night_brightness", setting="nightBrightness", native_min_value=0, native_max_value=100, native_step=1,
        native_unit_of_measurement=PERCENTAGE, mode=NumberMode.SLIDER, entity_category=EntityCategory.CONFIG,
    ),
    SettingNumberDescription(
        key="back_to_home", setting="backToHomeSeconds", native_min_value=0, native_max_value=3600, native_step=5,
        native_unit_of_measurement=UnitOfTime.SECONDS, mode=NumberMode.BOX, entity_category=EntityCategory.CONFIG,
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
        async_add_entities(SettingNumber(coordinator, s, d) for s in new for d in NUMBERS)

    add_new()
    entry.async_on_unload(coordinator.async_add_listener(add_new))


class SettingNumber(RemoteScreensEntity, NumberEntity):
    entity_description: SettingNumberDescription

    def __init__(self, coordinator, screen_id: str, description: SettingNumberDescription) -> None:
        super().__init__(coordinator, screen_id, description.key)
        self.entity_description = description

    @property
    def native_value(self) -> float | None:
        return self.settings.get(self.entity_description.setting)

    async def async_set_native_value(self, value: float) -> None:
        await self.coordinator.async_set_settings(self.screen_id, {self.entity_description.setting: int(value)})
