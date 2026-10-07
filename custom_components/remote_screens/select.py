"""The page a screen shows."""

from __future__ import annotations

from homeassistant.components.select import SelectEntity
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .api import RemoteScreensError
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
        async_add_entities(PageSelect(coordinator, s) for s in new)

    add_new()
    entry.async_on_unload(coordinator.async_add_listener(add_new))


class PageSelect(RemoteScreensEntity, SelectEntity):
    """The page of the first tablet showing the screen; choosing one opens it on all of them."""

    def __init__(self, coordinator, screen_id: str) -> None:
        super().__init__(coordinator, screen_id, "page")

    @property
    def options(self) -> list[str]:
        return list(self.screen.get("pages") or [])

    @property
    def current_option(self) -> str | None:
        online = [p for p in self.screen.get("players") or [] if p.get("online")]
        pages = self.options
        if not online:
            return None
        i = online[0].get("page", 0)
        return pages[i] if 0 <= i < len(pages) else None

    async def async_select_option(self, option: str) -> None:
        try:
            await self.coordinator.client.open_page(option, [self.screen_id])
        except RemoteScreensError as err:
            raise HomeAssistantError(str(err)) from err
        await self.coordinator.async_request_refresh()
