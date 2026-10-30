"""Each tablet's own device: its sensors, diagnostics and location."""

from __future__ import annotations

from unittest.mock import AsyncMock

from homeassistant.core import HomeAssistant
from homeassistant.helpers import device_registry as dr, entity_registry as er

from pytest_homeassistant_custom_component.common import async_fire_time_changed

from custom_components.remote_screens.api import RemoteScreensNotFoundError
from custom_components.remote_screens.const import DOMAIN, SCAN_INTERVAL
from homeassistant.util import dt as dt_util

from .test_init import setup


async def refresh(hass: HomeAssistant) -> None:
    async_fire_time_changed(hass, dt_util.utcnow() + SCAN_INTERVAL)
    await hass.async_block_till_done()


async def test_a_tablet_is_its_own_device_under_its_screen(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    registry = dr.async_get(hass)
    tablet = registry.async_get_device(identifiers={(DOMAIN, "player_tablet-1")})
    screen = registry.async_get_device(identifiers={(DOMAIN, "demo")})

    assert tablet is not None
    assert tablet.via_device_id == screen.id
    assert tablet.model == "Pixel Tablet"
    assert tablet.manufacturer == "Google"


async def test_sensors_follow_the_reported_hardware(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)

    assert hass.states.get("sensor.pixel_tablet_illuminance").state == "182.0"
    assert hass.states.get("binary_sensor.pixel_tablet_proximity").state == "off"
    assert hass.states.get("binary_sensor.pixel_tablet_moving").state == "on"
    assert hass.states.get("sensor.pixel_tablet_orientation").state == "landscape"
    assert hass.states.get("sensor.pixel_tablet_pressure") is None
    assert hass.states.get("sensor.pixel_tablet_humidity") is None


async def test_diagnostics_are_reported(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)

    assert hass.states.get("sensor.pixel_tablet_battery").state == "90"
    assert hass.states.get("binary_sensor.pixel_tablet_charging").state == "on"
    assert hass.states.get("sensor.pixel_tablet_battery_health").state == "good"
    assert hass.states.get("sensor.pixel_tablet_wi_fi_network").state == "HomeNet"
    assert hass.states.get("sensor.pixel_tablet_kiosk").state == "locked"
    assert hass.states.get("sensor.pixel_tablet_android_version").state == "14"
    assert er.async_get(hass).async_get("sensor.pixel_tablet_battery_power").disabled_by is er.RegistryEntryDisabler.INTEGRATION


async def test_an_offline_tablet_without_telemetry_only_gets_connectivity(
    hass: HomeAssistant, client: AsyncMock, entry
) -> None:
    await setup(hass, entry)

    assert hass.states.get("binary_sensor.spare_tablet_online").state == "off"
    assert hass.states.get("sensor.spare_tablet_battery") is None
    assert hass.states.get("device_tracker.spare_tablet_location") is None


async def test_the_tracker_appears_with_the_first_fix(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    assert hass.states.get("device_tracker.pixel_tablet_location") is None

    client.players_data[0]["telemetry"]["location"] = {
        "latitude": 52.1, "longitude": 4.3, "accuracyM": 20.0, "fixedAt": 1,
    }
    await refresh(hass)

    tracker = hass.states.get("device_tracker.pixel_tablet_location")
    assert tracker is not None
    assert tracker.attributes["latitude"] == 52.1
    assert tracker.attributes["longitude"] == 4.3
    assert tracker.attributes["gps_accuracy"] == 20


async def test_the_sharing_switches_reach_the_add_on(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    await hass.services.async_call(
        "switch", "turn_on", {"entity_id": "switch.living_room_share_tablet_location"}, blocking=True
    )
    client.set_settings.assert_awaited_with("demo", {"shareLocation": True})

    await hass.services.async_call(
        "switch", "turn_off", {"entity_id": "switch.living_room_report_tablet_sensors"}, blocking=True
    )
    client.set_settings.assert_awaited_with("demo", {"reportTelemetry": False})


async def test_a_tablet_device_targets_only_that_tablet(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    await setup(hass, entry)
    tablet = dr.async_get(hass).async_get_device(identifiers={(DOMAIN, "player_tablet-1")})

    await hass.services.async_call(DOMAIN, "wake", {"screens": [tablet.id]}, blocking=True)

    client.wake.assert_awaited_with(["tablet-1"])


async def test_an_older_add_on_still_lists_its_tablets(hass: HomeAssistant, client: AsyncMock, entry) -> None:
    client.players.side_effect = RemoteScreensNotFoundError("Not found: /players")
    await setup(hass, entry)

    assert hass.states.get("binary_sensor.pixel_tablet_online").state == "on"
    assert hass.states.get("sensor.pixel_tablet_battery").state == "90"
    assert hass.states.get("sensor.pixel_tablet_illuminance") is None
