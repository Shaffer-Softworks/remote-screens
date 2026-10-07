"""Sets up the integration, by itself when the add-on announces it or by hand with its key."""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

import voluptuous as vol

from homeassistant.config_entries import ConfigFlow, ConfigFlowResult
from homeassistant.const import CONF_HOST, CONF_PORT
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.service_info.hassio import HassioServiceInfo

from .api import RemoteScreensAuthError, RemoteScreensClient, RemoteScreensError
from .const import CONF_API_KEY, DEFAULT_HOST, DEFAULT_PORT, DOMAIN


class RemoteScreensConfigFlow(ConfigFlow, domain=DOMAIN):
    """One entry for the add-on."""

    VERSION = 1

    def __init__(self) -> None:
        self._discovered: dict[str, Any] = {}

    async def _check(self, data: Mapping[str, Any]) -> str | None:
        """The error key for [data], or None when the add-on answers to it."""
        client = RemoteScreensClient(
            async_get_clientsession(self.hass), data[CONF_HOST], data[CONF_PORT], data[CONF_API_KEY]
        )
        try:
            await client.screens()
        except RemoteScreensAuthError:
            return "invalid_auth"
        except RemoteScreensError:
            return "cannot_connect"
        return None

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        await self.async_set_unique_id(DOMAIN)
        self._abort_if_unique_id_configured()
        errors: dict[str, str] = {}
        if user_input is not None:
            if (error := await self._check(user_input)) is None:
                return self.async_create_entry(title="Remote Screens", data=user_input)
            errors["base"] = error
        defaults = user_input or {CONF_HOST: DEFAULT_HOST, CONF_PORT: DEFAULT_PORT}
        return self.async_show_form(
            step_id="user",
            data_schema=vol.Schema(
                {
                    vol.Required(CONF_HOST, default=defaults.get(CONF_HOST, DEFAULT_HOST)): str,
                    vol.Required(CONF_PORT, default=defaults.get(CONF_PORT, DEFAULT_PORT)): int,
                    vol.Required(CONF_API_KEY): str,
                }
            ),
            errors=errors,
        )

    async def async_step_hassio(self, discovery_info: HassioServiceInfo) -> ConfigFlowResult:
        """The add-on announced itself with its address and key."""
        config = discovery_info.config
        data = {
            CONF_HOST: config.get(CONF_HOST, DEFAULT_HOST),
            CONF_PORT: int(config.get(CONF_PORT, DEFAULT_PORT)),
            CONF_API_KEY: config[CONF_API_KEY],
        }
        await self.async_set_unique_id(DOMAIN)
        self._abort_if_unique_id_configured(updates=data)
        self._discovered = data
        return await self.async_step_hassio_confirm()

    async def async_step_hassio_confirm(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        errors: dict[str, str] = {}
        if user_input is not None:
            if (error := await self._check(self._discovered)) is None:
                return self.async_create_entry(title="Remote Screens", data=self._discovered)
            errors["base"] = error
        return self.async_show_form(step_id="hassio_confirm", errors=errors)

    async def async_step_reauth(self, entry_data: Mapping[str, Any]) -> ConfigFlowResult:
        return await self.async_step_reauth_confirm()

    async def async_step_reauth_confirm(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        entry = self._get_reauth_entry()
        errors: dict[str, str] = {}
        if user_input is not None:
            data = {**entry.data, CONF_API_KEY: user_input[CONF_API_KEY]}
            if (error := await self._check(data)) is None:
                return self.async_update_reload_and_abort(entry, data=data)
            errors["base"] = error
        return self.async_show_form(
            step_id="reauth_confirm",
            data_schema=vol.Schema({vol.Required(CONF_API_KEY): str}),
            errors=errors,
        )
