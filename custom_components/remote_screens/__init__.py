"""Remote Screens: settings entities and services for the screens of the Remote Screens add-on."""

from __future__ import annotations

from typing import Any

import voluptuous as vol

from homeassistant.const import CONF_HOST, CONF_PORT, Platform
from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.exceptions import HomeAssistantError, ServiceValidationError
from homeassistant.helpers import config_validation as cv, device_registry as dr
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.typing import ConfigType

from .api import RemoteScreensClient, RemoteScreensError
from .const import (
    ATTR_BUTTONS,
    ATTR_CAMERA,
    ATTR_ICON,
    ATTR_LEVEL,
    ATTR_MESSAGE,
    ATTR_PAGE,
    ATTR_SCREENS,
    ATTR_TAG,
    ATTR_TIMEOUT,
    ATTR_TITLE,
    CONF_API_KEY,
    DOMAIN,
    LEVELS,
    SERVICE_CLOSE,
    SERVICE_OPEN_PAGE,
    SERVICE_SHOW_ALERT,
    SERVICE_WAKE,
)
from .coordinator import RemoteScreensConfigEntry, RemoteScreensCoordinator

PLATFORMS = [Platform.BINARY_SENSOR, Platform.NUMBER, Platform.SELECT, Platform.SWITCH, Platform.TEXT, Platform.TIME]

CONFIG_SCHEMA = cv.config_entry_only_config_schema(DOMAIN)

SCREENS = vol.Optional(ATTR_SCREENS, default=list)

BUTTON_SCHEMA = vol.Schema(
    {
        vol.Required("title"): cv.string,
        vol.Optional("action"): cv.string,
        vol.Optional("service"): cv.string,
        vol.Optional("data"): dict,
    }
)

SHOW_ALERT_SCHEMA = vol.Schema(
    {
        SCREENS: vol.All(cv.ensure_list, [cv.string]),
        vol.Required(ATTR_TITLE): cv.string,
        vol.Optional(ATTR_MESSAGE, default=""): cv.string,
        vol.Optional(ATTR_ICON): cv.string,
        vol.Optional(ATTR_LEVEL, default="info"): vol.In(LEVELS),
        vol.Optional(ATTR_CAMERA): cv.entity_domain("camera"),
        vol.Optional(ATTR_BUTTONS, default=list): vol.All(cv.ensure_list, vol.Length(max=2), [BUTTON_SCHEMA]),
        vol.Optional(ATTR_TIMEOUT, default=30): vol.All(vol.Coerce(int), vol.Range(min=0, max=3600)),
        vol.Optional(ATTR_TAG): cv.string,
    }
)
OPEN_PAGE_SCHEMA = vol.Schema({SCREENS: vol.All(cv.ensure_list, [cv.string]), vol.Required(ATTR_PAGE): cv.string})
TARGETS_SCHEMA = vol.Schema({SCREENS: vol.All(cv.ensure_list, [cv.string])})


async def async_setup(hass: HomeAssistant, config: ConfigType) -> bool:
    """The services, which reach whichever add-on is set up."""

    def client() -> RemoteScreensClient:
        entries = hass.config_entries.async_loaded_entries(DOMAIN)
        if not entries:
            raise ServiceValidationError(translation_domain=DOMAIN, translation_key="not_loaded")
        return entries[0].runtime_data.client

    def targets(call: ServiceCall) -> list[str]:
        """Screen ids from the screen devices chosen; none is every screen."""
        registry = dr.async_get(hass)
        ids: list[str] = []
        for device_id in call.data[ATTR_SCREENS]:
            device = registry.async_get(device_id)
            screen = next((i for d, i in device.identifiers if d == DOMAIN), None) if device else None
            if screen is None:
                raise ServiceValidationError(
                    translation_domain=DOMAIN, translation_key="unknown_screen", translation_placeholders={"id": device_id}
                )
            ids.append(screen)
        return ids

    async def run(action) -> None:
        try:
            await action
        except RemoteScreensError as err:
            raise HomeAssistantError(str(err)) from err

    async def show_alert(call: ServiceCall) -> None:
        alert: dict[str, Any] = {
            k: v for k, v in call.data.items() if k not in (ATTR_SCREENS,) and v is not None
        }
        alert["targets"] = targets(call)
        await run(client().show_alert(alert))

    async def open_page(call: ServiceCall) -> None:
        await run(client().open_page(call.data[ATTR_PAGE], targets(call)))

    async def wake(call: ServiceCall) -> None:
        await run(client().wake(targets(call)))

    async def close(call: ServiceCall) -> None:
        await run(client().close(targets(call)))

    hass.services.async_register(DOMAIN, SERVICE_SHOW_ALERT, show_alert, SHOW_ALERT_SCHEMA)
    hass.services.async_register(DOMAIN, SERVICE_OPEN_PAGE, open_page, OPEN_PAGE_SCHEMA)
    hass.services.async_register(DOMAIN, SERVICE_WAKE, wake, TARGETS_SCHEMA)
    hass.services.async_register(DOMAIN, SERVICE_CLOSE, close, TARGETS_SCHEMA)
    return True


async def async_setup_entry(hass: HomeAssistant, entry: RemoteScreensConfigEntry) -> bool:
    client = RemoteScreensClient(
        async_get_clientsession(hass), entry.data[CONF_HOST], entry.data[CONF_PORT], entry.data[CONF_API_KEY]
    )
    coordinator = RemoteScreensCoordinator(hass, entry, client)
    await coordinator.async_config_entry_first_refresh()
    entry.runtime_data = coordinator
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: RemoteScreensConfigEntry) -> bool:
    return await hass.config_entries.async_unload_platforms(entry, PLATFORMS)


async def async_remove_config_entry_device(
    hass: HomeAssistant, entry: RemoteScreensConfigEntry, device: dr.DeviceEntry
) -> bool:
    """A screen deleted in the editor can be removed; one that still exists comes back on the next poll."""
    return not any(d == DOMAIN and i in entry.runtime_data.data for d, i in device.identifiers)
