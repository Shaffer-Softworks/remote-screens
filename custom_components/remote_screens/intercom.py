"""Bridge a dashboard card to the add-on's intercom signaling. The browser never opens port 8766."""

from __future__ import annotations

import logging
from pathlib import Path

import voluptuous as vol
from aiohttp import ClientWebSocketResponse, WSMsgType

from homeassistant.components import websocket_api
from homeassistant.components.http import StaticPathConfig
from homeassistant.const import CONF_HOST
from homeassistant.core import HomeAssistant
from homeassistant.helpers.aiohttp_client import async_get_clientsession

from .const import DEFAULT_HOST, DOMAIN, INTERCOM_PORT

_LOGGER = logging.getLogger(__name__)

CARD_URL = "/api/remote_screens/intercom-card.js"
BRIDGES = f"{DOMAIN}_intercom"

_SUBSCRIBE = {
    vol.Required("type"): "remote_screens/intercom/subscribe",
    vol.Required("session"): str,
}
_SEND = {
    vol.Required("type"): "remote_screens/intercom/send",
    vol.Required("session"): str,
    vol.Required("message"): str,
}


def _sockets(hass: HomeAssistant) -> dict[str, ClientWebSocketResponse]:
    return hass.data.setdefault(BRIDGES, {})


async def async_setup_intercom(hass: HomeAssistant) -> None:
    """Register the card and the commands that carry its signaling."""
    websocket_api.async_register_command(hass, _subscribe)
    websocket_api.async_register_command(hass, _send)
    http = hass.http
    if http is None:
        _LOGGER.debug("The intercom card waits until the http component is set up")
        return
    card = Path(__file__).parent / "frontend" / "intercom-card.js"
    try:
        await http.async_register_static_paths([StaticPathConfig(CARD_URL, str(card), False)])
    except AttributeError:
        http.register_static_path(CARD_URL, str(card), cache_headers=False)
    try:
        from homeassistant.components.frontend import add_extra_js_url
    except ImportError:
        _LOGGER.debug("The intercom card is served at %s", CARD_URL)
        return
    add_extra_js_url(hass, CARD_URL)


@websocket_api.websocket_command(_SUBSCRIBE)
@websocket_api.async_response
async def _subscribe(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Open one signaling socket for this card and forward what the add-on sends."""
    entry = next(iter(hass.config_entries.async_loaded_entries(DOMAIN)), None)
    if entry is None:
        connection.send_error(msg["id"], "not_loaded", "Remote Screens is not set up")
        return
    host = entry.data.get(CONF_HOST, DEFAULT_HOST)
    try:
        upstream = await async_get_clientsession(hass).ws_connect(f"ws://{host}:{INTERCOM_PORT}/webrtc", heartbeat=30)
    except OSError as err:
        connection.send_error(msg["id"], "connect_failed", str(err))
        return
    session = msg["session"]
    _sockets(hass)[session] = upstream
    connection.send_result(msg["id"])

    async def forward() -> None:
        try:
            async for incoming in upstream:
                if incoming.type == WSMsgType.TEXT:
                    connection.send_message(websocket_api.event_message(msg["id"], {"message": incoming.data}))
                elif incoming.type in (WSMsgType.CLOSE, WSMsgType.CLOSED, WSMsgType.ERROR):
                    break
        finally:
            _sockets(hass).pop(session, None)

    task = hass.async_create_task(forward())

    def close() -> None:
        task.cancel()
        hass.async_create_task(upstream.close())
        _sockets(hass).pop(session, None)

    connection.subscriptions[msg["id"]] = close


@websocket_api.websocket_command(_SEND)
@websocket_api.async_response
async def _send(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Forward one signaling frame from the card to the add-on."""
    upstream = _sockets(hass).get(msg["session"])
    if upstream is None or upstream.closed:
        connection.send_error(msg["id"], "not_open", "No intercom session")
        return
    await upstream.send_str(msg["message"])
    connection.send_result(msg["id"])
