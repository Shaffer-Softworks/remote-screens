import esphome.codegen as cg
import esphome.config_validation as cv
from esphome.const import CONF_ID

DEPENDENCIES = ["lvgl", "api"]
AUTO_LOAD = ["json"]

CONF_SCREEN_ID = "screen_id"
CONF_PAGE_ID = "page_id"

ns = cg.esphome_ns.namespace("remote_screens_panel")
RemoteScreensPanel = ns.class_("RemoteScreensPanel", cg.Component)

CONFIG_SCHEMA = cv.Schema(
    {
        cv.GenerateID(): cv.declare_id(RemoteScreensPanel),
        cv.Required(CONF_SCREEN_ID): cv.string,
        cv.Required(CONF_PAGE_ID): cv.string,
    }
).extend(cv.COMPONENT_SCHEMA)


async def to_code(config):
    var = cg.new_Pvariable(config[CONF_ID])
    await cg.register_component(var, config)
    cg.add(var.set_screen_id(config[CONF_SCREEN_ID]))
    cg.add(var.set_page_id(config[CONF_PAGE_ID]))
