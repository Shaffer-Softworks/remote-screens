"""Setting the integration up."""

from __future__ import annotations

from unittest.mock import AsyncMock

from homeassistant import config_entries
from homeassistant.core import HomeAssistant
from homeassistant.data_entry_flow import FlowResultType
from homeassistant.helpers.service_info.hassio import HassioServiceInfo

from custom_components.remote_screens.api import RemoteScreensAuthError, RemoteScreensError
from custom_components.remote_screens.const import CONF_API_KEY, DOMAIN

from .conftest import ENTRY_DATA

DISCOVERY = HassioServiceInfo(
    config={"host": "127.0.0.1", "port": 8100, "api_key": "key"},
    name="Remote Screens",
    slug="remote_screens",
    uuid="1234",
)


async def test_by_hand(hass: HomeAssistant, client: AsyncMock) -> None:
    result = await hass.config_entries.flow.async_init(DOMAIN, context={"source": config_entries.SOURCE_USER})
    assert result["type"] is FlowResultType.FORM
    result = await hass.config_entries.flow.async_configure(result["flow_id"], ENTRY_DATA)
    assert result["type"] is FlowResultType.CREATE_ENTRY
    assert result["data"] == ENTRY_DATA


async def test_a_wrong_key_is_said_so(hass: HomeAssistant, client: AsyncMock) -> None:
    client.screens.side_effect = RemoteScreensAuthError("no")
    result = await hass.config_entries.flow.async_init(DOMAIN, context={"source": config_entries.SOURCE_USER})
    result = await hass.config_entries.flow.async_configure(result["flow_id"], ENTRY_DATA)
    assert result["errors"] == {"base": "invalid_auth"}

    client.screens.side_effect = RemoteScreensError("down")
    result = await hass.config_entries.flow.async_configure(result["flow_id"], ENTRY_DATA)
    assert result["errors"] == {"base": "cannot_connect"}


async def test_the_add_on_announces_itself(hass: HomeAssistant, client: AsyncMock) -> None:
    result = await hass.config_entries.flow.async_init(
        DOMAIN, context={"source": config_entries.SOURCE_HASSIO}, data=DISCOVERY
    )
    assert result["step_id"] == "hassio_confirm"
    result = await hass.config_entries.flow.async_configure(result["flow_id"], {})
    assert result["type"] is FlowResultType.CREATE_ENTRY
    assert result["data"][CONF_API_KEY] == "key"


async def test_a_new_key_from_the_add_on_updates_the_entry(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    entry.add_to_hass(hass)
    info = HassioServiceInfo(config={**DISCOVERY.config, "api_key": "new"}, name="", slug="", uuid="")
    result = await hass.config_entries.flow.async_init(DOMAIN, context={"source": config_entries.SOURCE_HASSIO}, data=info)
    assert result["type"] is FlowResultType.ABORT
    assert entry.data[CONF_API_KEY] == "new"


async def test_reauth(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    entry.add_to_hass(hass)
    result = await entry.start_reauth_flow(hass)
    assert result["step_id"] == "reauth_confirm"
    result = await hass.config_entries.flow.async_configure(result["flow_id"], {CONF_API_KEY: "fresh"})
    assert result["type"] is FlowResultType.ABORT
    assert result["reason"] == "reauth_successful"
    assert entry.data[CONF_API_KEY] == "fresh"
