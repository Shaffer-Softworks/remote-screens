"""What each tablet reports about itself: environment sensors and diagnostics."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime, UTC
from typing import Any

from homeassistant.components.sensor import (
    SensorDeviceClass,
    SensorEntity,
    SensorEntityDescription,
    SensorStateClass,
)
from homeassistant.const import (
    LIGHT_LUX,
    PERCENTAGE,
    SIGNAL_STRENGTH_DECIBELS_MILLIWATT,
    EntityCategory,
    UnitOfDataRate,
    UnitOfElectricPotential,
    UnitOfFrequency,
    UnitOfInformation,
    UnitOfPower,
    UnitOfPressure,
    UnitOfTemperature,
    UnitOfTime,
)
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .coordinator import RemoteScreensConfigEntry
from .entity import RemoteScreensTabletEntity, has_hardware, telemetry_of

MILLI_PER_UNIT = 1000

BATTERY_HEALTH = ["good", "overheat", "dead", "over_voltage", "cold", "failure", "unknown"]
CHARGER_TYPES = ["ac", "usb", "wireless", "dock", "none"]
KIOSK_STATES = ["off", "pinned", "locked"]
ORIENTATIONS = ["portrait", "landscape"]


def group(player: dict[str, Any], name: str) -> dict[str, Any]:
    return telemetry_of(player).get(name) or {}


def epoch_ms(value: int | None) -> datetime | None:
    return datetime.fromtimestamp(value / MILLI_PER_UNIT, UTC) if value else None


def battery_power(player: dict[str, Any]) -> float | None:
    battery = group(player, "battery")
    voltage_mv, current_ma = battery.get("voltageMv"), battery.get("currentMa")
    if voltage_mv is None or current_ma is None:
        return None
    return round(abs(voltage_mv * current_ma) / (MILLI_PER_UNIT * MILLI_PER_UNIT), 2)


@dataclass(frozen=True, kw_only=True)
class TabletSensorDescription(SensorEntityDescription):
    value_fn: Callable[[dict[str, Any]], Any]
    #: Only created when the tablet reports one of these in `hardware`.
    hardware: tuple[str, ...] = ()
    #: Off for what older players report too, outside of telemetry.
    needs_telemetry: bool = True


DIAGNOSTIC = EntityCategory.DIAGNOSTIC
MEASUREMENT = SensorStateClass.MEASUREMENT

SENSORS: tuple[TabletSensorDescription, ...] = (
    # Environment
    TabletSensorDescription(
        key="illuminance", hardware=("light",), device_class=SensorDeviceClass.ILLUMINANCE,
        native_unit_of_measurement=LIGHT_LUX, state_class=MEASUREMENT, suggested_display_precision=0,
        value_fn=lambda p: group(p, "environment").get("illuminanceLux"),
    ),
    TabletSensorDescription(
        key="pressure", hardware=("pressure",), device_class=SensorDeviceClass.ATMOSPHERIC_PRESSURE,
        native_unit_of_measurement=UnitOfPressure.HPA, state_class=MEASUREMENT, suggested_display_precision=1,
        value_fn=lambda p: group(p, "environment").get("pressureHpa"),
    ),
    TabletSensorDescription(
        key="ambient_temperature", hardware=("ambient_temp",), device_class=SensorDeviceClass.TEMPERATURE,
        native_unit_of_measurement=UnitOfTemperature.CELSIUS, state_class=MEASUREMENT, suggested_display_precision=1,
        value_fn=lambda p: group(p, "environment").get("temperatureC"),
    ),
    TabletSensorDescription(
        key="humidity", hardware=("humidity",), device_class=SensorDeviceClass.HUMIDITY,
        native_unit_of_measurement=PERCENTAGE, state_class=MEASUREMENT, suggested_display_precision=0,
        value_fn=lambda p: group(p, "environment").get("humidityPercent"),
    ),
    TabletSensorDescription(
        key="orientation", device_class=SensorDeviceClass.ENUM, options=ORIENTATIONS,
        value_fn=lambda p: group(p, "motion").get("orientation"),
    ),
    TabletSensorDescription(
        key="last_motion", hardware=("accelerometer", "significant_motion"), device_class=SensorDeviceClass.TIMESTAMP,
        value_fn=lambda p: epoch_ms(group(p, "motion").get("lastMotionAt")),
    ),
    # Battery
    TabletSensorDescription(
        key="battery", device_class=SensorDeviceClass.BATTERY, native_unit_of_measurement=PERCENTAGE,
        state_class=MEASUREMENT, entity_category=DIAGNOSTIC, needs_telemetry=False,
        value_fn=lambda p: p.get("battery"),
    ),
    TabletSensorDescription(
        key="battery_temperature", device_class=SensorDeviceClass.TEMPERATURE,
        native_unit_of_measurement=UnitOfTemperature.CELSIUS, state_class=MEASUREMENT, entity_category=DIAGNOSTIC,
        suggested_display_precision=1, value_fn=lambda p: group(p, "battery").get("temperatureC"),
    ),
    TabletSensorDescription(
        key="battery_health", device_class=SensorDeviceClass.ENUM, options=BATTERY_HEALTH,
        entity_category=DIAGNOSTIC, value_fn=lambda p: group(p, "battery").get("health"),
    ),
    TabletSensorDescription(
        key="battery_power", device_class=SensorDeviceClass.POWER, native_unit_of_measurement=UnitOfPower.WATT,
        state_class=MEASUREMENT, entity_category=DIAGNOSTIC, entity_registry_enabled_default=False,
        value_fn=battery_power,
    ),
    TabletSensorDescription(
        key="battery_voltage", device_class=SensorDeviceClass.VOLTAGE,
        native_unit_of_measurement=UnitOfElectricPotential.MILLIVOLT, state_class=MEASUREMENT,
        entity_category=DIAGNOSTIC, entity_registry_enabled_default=False,
        value_fn=lambda p: group(p, "battery").get("voltageMv"),
    ),
    TabletSensorDescription(
        key="charger_type", device_class=SensorDeviceClass.ENUM, options=CHARGER_TYPES,
        entity_category=DIAGNOSTIC, entity_registry_enabled_default=False,
        value_fn=lambda p: group(p, "battery").get("chargerType"),
    ),
    TabletSensorDescription(
        key="battery_cycles", state_class=SensorStateClass.TOTAL_INCREASING, entity_category=DIAGNOSTIC,
        entity_registry_enabled_default=False, value_fn=lambda p: group(p, "battery").get("cycles"),
    ),
    # Wi-Fi
    TabletSensorDescription(
        key="wifi_ssid", entity_category=DIAGNOSTIC, value_fn=lambda p: group(p, "network").get("ssid"),
    ),
    TabletSensorDescription(
        key="wifi_signal", device_class=SensorDeviceClass.SIGNAL_STRENGTH,
        native_unit_of_measurement=SIGNAL_STRENGTH_DECIBELS_MILLIWATT, state_class=MEASUREMENT,
        entity_category=DIAGNOSTIC, value_fn=lambda p: group(p, "network").get("rssiDbm"),
    ),
    TabletSensorDescription(
        key="wifi_bssid", entity_category=DIAGNOSTIC, entity_registry_enabled_default=False,
        value_fn=lambda p: group(p, "network").get("bssid"),
    ),
    TabletSensorDescription(
        key="ip_address", entity_category=DIAGNOSTIC, entity_registry_enabled_default=False,
        value_fn=lambda p: group(p, "network").get("ipAddress"),
    ),
    TabletSensorDescription(
        key="wifi_link_speed", device_class=SensorDeviceClass.DATA_RATE,
        native_unit_of_measurement=UnitOfDataRate.MEGABITS_PER_SECOND, state_class=MEASUREMENT,
        entity_category=DIAGNOSTIC, entity_registry_enabled_default=False,
        value_fn=lambda p: group(p, "network").get("linkSpeedMbps"),
    ),
    TabletSensorDescription(
        key="wifi_frequency", device_class=SensorDeviceClass.FREQUENCY,
        native_unit_of_measurement=UnitOfFrequency.MEGAHERTZ, entity_category=DIAGNOSTIC,
        entity_registry_enabled_default=False, value_fn=lambda p: group(p, "network").get("frequencyMhz"),
    ),
    # System
    TabletSensorDescription(
        key="storage_free", device_class=SensorDeviceClass.DATA_SIZE,
        native_unit_of_measurement=UnitOfInformation.BYTES, suggested_unit_of_measurement=UnitOfInformation.GIGABYTES,
        state_class=MEASUREMENT, entity_category=DIAGNOSTIC, suggested_display_precision=1,
        value_fn=lambda p: group(p, "system").get("storageFreeBytes"),
    ),
    TabletSensorDescription(
        key="memory_available", device_class=SensorDeviceClass.DATA_SIZE,
        native_unit_of_measurement=UnitOfInformation.BYTES, suggested_unit_of_measurement=UnitOfInformation.GIGABYTES,
        state_class=MEASUREMENT, entity_category=DIAGNOSTIC, suggested_display_precision=1,
        value_fn=lambda p: group(p, "system").get("memoryAvailableBytes"),
    ),
    TabletSensorDescription(
        key="uptime", device_class=SensorDeviceClass.DURATION, native_unit_of_measurement=UnitOfTime.SECONDS,
        suggested_unit_of_measurement=UnitOfTime.HOURS, entity_category=DIAGNOSTIC,
        entity_registry_enabled_default=False, value_fn=lambda p: group(p, "system").get("uptimeSeconds"),
    ),
    TabletSensorDescription(
        key="android_version", entity_category=DIAGNOSTIC, value_fn=lambda p: group(p, "system").get("osVersion"),
    ),
    TabletSensorDescription(
        key="security_patch", entity_category=DIAGNOSTIC, entity_registry_enabled_default=False,
        value_fn=lambda p: group(p, "system").get("securityPatch"),
    ),
    TabletSensorDescription(
        key="app_version", entity_category=DIAGNOSTIC, value_fn=lambda p: group(p, "system").get("appVersionName"),
    ),
    TabletSensorDescription(
        key="kiosk_mode", device_class=SensorDeviceClass.ENUM, options=KIOSK_STATES, entity_category=DIAGNOSTIC,
        needs_telemetry=False, value_fn=lambda p: p.get("kiosk"),
    ),
    TabletSensorDescription(
        key="last_report", device_class=SensorDeviceClass.TIMESTAMP, entity_category=DIAGNOSTIC,
        value_fn=lambda p: epoch_ms(p.get("telemetryAt")),
    ),
)


async def async_setup_entry(
    hass: HomeAssistant, entry: RemoteScreensConfigEntry, async_add_entities: AddConfigEntryEntitiesCallback
) -> None:
    coordinator = entry.runtime_data
    known: set[tuple[str, str]] = set()

    def add_new() -> None:
        """A tablet's sensors appear once it reports the hardware behind them."""
        new: list[TabletSensor] = []
        for device_id, player in coordinator.players.items():
            if not player.get("online"):
                continue
            has_telemetry = bool(player.get("telemetry"))
            for description in SENSORS:
                if (device_id, description.key) in known:
                    continue
                if description.needs_telemetry and not has_telemetry:
                    continue
                if not has_hardware(player, *description.hardware):
                    continue
                known.add((device_id, description.key))
                new.append(TabletSensor(coordinator, device_id, description))
        async_add_entities(new)

    add_new()
    entry.async_on_unload(coordinator.async_add_listener(add_new))


class TabletSensor(RemoteScreensTabletEntity, SensorEntity):
    entity_description: TabletSensorDescription

    def __init__(self, coordinator, device_id: str, description: TabletSensorDescription) -> None:
        super().__init__(coordinator, device_id, description.key)
        self.entity_description = description

    @property
    def native_value(self) -> Any:
        return self.entity_description.value_fn(self.player)
