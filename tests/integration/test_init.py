"""The entities and services of a screen."""

from __future__ import annotations

from unittest.mock import AsyncMock

import pytest

from homeassistant.config_entries import ConfigEntryState
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import ServiceValidationError
from homeassistant.helpers import device_registry as dr

from custom_components.remote_screens.api import RemoteScreensAuthError
from custom_components.remote_screens.const import DOMAIN


async def setup(hass: HomeAssistant, entry) -> None:
    entry.add_to_hass(hass)
    assert await hass.config_entries.async_setup(entry.entry_id)
    await hass.async_block_till_done()


async def test_a_screen_gets_its_entities(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    assert hass.states.get("number.living_room_brightness").state == "80"
    assert hass.states.get("switch.living_room_dark_mode").state == "off"
    assert hass.states.get("switch.living_room_dark_at_night").state == "on"
    assert hass.states.get("time.living_room_night_starts").state == "unknown"
    page = hass.states.get("select.living_room_page")
    assert page.state == "Rooms"
    assert page.attributes["options"] == ["Home", "Rooms", "More"]
    assert hass.states.get("binary_sensor.living_room_connected").state == "on"


async def test_settings_go_to_the_add_on(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    await hass.services.async_call("number", "set_value", {"entity_id": "number.living_room_brightness", "value": 42}, blocking=True)
    client.set_settings.assert_awaited_with("demo", {"brightness": 42})
    assert hass.states.get("number.living_room_brightness").state == "42"

    await hass.services.async_call("switch", "turn_on", {"entity_id": "switch.living_room_dark_mode"}, blocking=True)
    client.set_settings.assert_awaited_with("demo", {"darkMode": True})


async def test_the_kiosk_lock_and_its_pin_go_to_the_add_on(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    await hass.services.async_call("switch", "turn_on", {"entity_id": "switch.living_room_kiosk_lock"}, blocking=True)
    client.set_settings.assert_awaited_with("demo", {"kiosk": True})
    assert hass.states.get("switch.living_room_kiosk_lock").state == "on"

    await hass.services.async_call("text", "set_value", {"entity_id": "text.living_room_kiosk_pin", "value": "2468"}, blocking=True)
    client.set_settings.assert_awaited_with("demo", {"kioskPin": "2468"})

    await hass.services.async_call("text", "set_value", {"entity_id": "text.living_room_kiosk_pin", "value": ""}, blocking=True)
    client.set_settings.assert_awaited_with("demo", {"kioskPin": None})

    with pytest.raises(ValueError):
        await hass.services.async_call("text", "set_value", {"entity_id": "text.living_room_kiosk_pin", "value": "12"}, blocking=True)


async def test_one_end_of_the_night_brings_the_other(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    await hass.services.async_call("time", "set_value", {"entity_id": "time.living_room_night_starts", "time": "23:30"}, blocking=True)
    client.set_settings.assert_awaited_with("demo", {"nightStart": "23:30", "nightEnd": "07:00"})


async def test_choosing_a_page_opens_it(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    await hass.services.async_call("select", "select_option", {"entity_id": "select.living_room_page", "option": "More"}, blocking=True)
    client.open_page.assert_awaited_with("More", ["demo"])


async def test_services_reach_the_chosen_screens(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    device = dr.async_get(hass).async_get_device(identifiers={(DOMAIN, "demo")})

    await hass.services.async_call(DOMAIN, "show_alert", {"title": "Door", "screens": [device.id], "buttons": [{"title": "Open"}]}, blocking=True)
    alert = client.show_alert.await_args.args[0]
    assert alert["targets"] == ["demo"]
    assert alert["title"] == "Door"
    assert alert["level"] == "info"
    assert alert["buttons"] == [{"title": "Open"}]

    await hass.services.async_call(DOMAIN, "open_page", {"page": "Rooms"}, blocking=True)
    client.open_page.assert_awaited_with("Rooms", [])

    await hass.services.async_call(DOMAIN, "wake", {"screens": device.id}, blocking=True)
    client.wake.assert_awaited_with(["demo"])

    with pytest.raises(ServiceValidationError):
        await hass.services.async_call(DOMAIN, "close", {"screens": ["not-a-device"]}, blocking=True)


async def test_notifications_require_explicit_recipients(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    with pytest.raises(ServiceValidationError):
        await hass.services.async_call(DOMAIN, "send_notification", {"title": "Laundry"}, blocking=True)
    client.send_notification.assert_not_awaited()


async def test_notification_targets_and_results(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    tablet = dr.async_get(hass).async_get_device(identifiers={(DOMAIN, "player_tablet-1")})
    client.send_notification.return_value = {"devices": [{"deviceId": "tablet-1", "name": "Pixel Tablet", "status": "sent"}]}
    result = await hass.services.async_call(
        DOMAIN, "send_notification", {"title": "Laundry", "screens": [tablet.id]},
        blocking=True, return_response=True,
    )
    payload = client.send_notification.await_args.args[0]
    assert payload["targets"] == ["tablet-1"]
    assert payload["wake"] is False
    assert payload["presentation"] == "banner"
    assert "timeout" not in payload
    assert result == client.send_notification.return_value

    await hass.services.async_call(
        DOMAIN, "send_notification", {"title": "Hello", "all": True}, blocking=True
    )
    assert client.send_notification.await_args.args[0]["all"] is True
    assert client.send_notification.await_args.args[0]["targets"] == []

    with pytest.raises(ServiceValidationError):
        await hass.services.async_call(
            DOMAIN, "send_notification", {"title": "Hello", "all": True, "screens": [tablet.id]}, blocking=True
        )


async def test_a_refused_key_asks_for_a_new_one(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    client.screens.side_effect = RemoteScreensAuthError("no")
    entry.add_to_hass(hass)
    await hass.config_entries.async_setup(entry.entry_id)
    await hass.async_block_till_done()
    assert entry.state is ConfigEntryState.SETUP_ERROR
    assert any(f["context"]["source"] == "reauth" for f in hass.config_entries.flow.async_progress())


async def test_unload(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    assert await hass.config_entries.async_unload(entry.entry_id)
    assert entry.state is ConfigEntryState.NOT_LOADED
