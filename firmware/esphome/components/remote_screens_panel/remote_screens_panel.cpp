#include "remote_screens_panel.h"

#include "esphome/core/log.h"

#include <cstdlib>
#include <cstring>

namespace esphome {
namespace remote_screens_panel {

static const char *const TAG = "remote_screens_panel";
static const size_t MAX_LAYOUT_BYTES = 24 * 1024;
static const int PAYLOAD_VERSION = 1;
static const int FULL_PERCENT = 100;

static lv_obj_t *active_screen() {
#if LVGL_VERSION_MAJOR >= 9
  return lv_screen_active();
#else
  return lv_scr_act();
#endif
}

static lv_obj_t *make_button(lv_obj_t *parent) {
#if LVGL_VERSION_MAJOR >= 9
  return lv_button_create(parent);
#else
  return lv_btn_create(parent);
#endif
}

static lv_obj_t *make_tabs(lv_obj_t *parent) {
#if LVGL_VERSION_MAJOR >= 9
  return lv_tabview_create(parent);
#else
  return lv_tabview_create(parent, LV_DIR_TOP, 32);
#endif
}

static void lock_lvgl() {
#if LVGL_VERSION_MAJOR >= 9
  lv_lock();
#endif
}

static void unlock_lvgl() {
#if LVGL_VERSION_MAJOR >= 9
  lv_unlock();
#endif
}

static lv_color_t color_of(const char *hex) {
  return lv_color_hex(std::strtoul(hex == nullptr ? "000000" : hex, nullptr, 16));
}

static void apply_span(lv_obj_t *obj, const char *token, bool width) {
  lv_coord_t size = LV_SIZE_CONTENT;
  if (token != nullptr && std::strcmp(token, "fill") == 0) {
    size = lv_pct(FULL_PERCENT);
  } else if (token != nullptr && token[0] != '\0' && std::strcmp(token, "wrap") != 0) {
    size = std::atoi(token);
  }
  if (width) {
    lv_obj_set_width(obj, size);
  } else {
    lv_obj_set_height(obj, size);
  }
}

static void apply_chrome(lv_obj_t *obj, JsonObject widget) {
  apply_span(obj, widget["w"] | "wrap", true);
  apply_span(obj, widget["h"] | "wrap", false);
  int grow = widget["grow"] | 0;
  if (grow > 0) lv_obj_set_flex_grow(obj, grow);
  int pad = widget["pad"] | 0;
  if (pad > 0) lv_obj_set_style_pad_all(obj, pad, 0);
  int radius = widget["radius"] | 0;
  if (radius > 0) lv_obj_set_style_radius(obj, radius, 0);
  int border = widget["border"] | 0;
  if (border > 0) lv_obj_set_style_border_width(obj, border, 0);
  if (!widget["edge"].isNull()) lv_obj_set_style_border_color(obj, color_of(widget["edge"]), 0);
  if (!widget["color"].isNull()) lv_obj_set_style_text_color(obj, color_of(widget["color"]), 0);
  if (!widget["bg"].isNull()) {
    lv_obj_set_style_bg_color(obj, color_of(widget["bg"]), 0);
    lv_obj_set_style_bg_opa(obj, LV_OPA_COVER, 0);
  }
  const char *align = widget["align"] | "";
  if (std::strcmp(align, "center") == 0) lv_obj_set_style_text_align(obj, LV_TEXT_ALIGN_CENTER, 0);
  if (std::strcmp(align, "end") == 0) lv_obj_set_style_text_align(obj, LV_TEXT_ALIGN_RIGHT, 0);
}

static void flex_flow(lv_obj_t *obj, const char *kind) {
  lv_obj_set_layout(obj, LV_LAYOUT_FLEX);
  lv_flex_flow_t flow = LV_FLEX_FLOW_COLUMN;
  if (std::strcmp(kind, "row") == 0) flow = LV_FLEX_FLOW_ROW;
  if (std::strcmp(kind, "flow") == 0) flow = LV_FLEX_FLOW_ROW_WRAP;
  lv_obj_set_flex_flow(obj, flow);
  lv_obj_set_style_bg_opa(obj, LV_OPA_TRANSP, 0);
}

void RemoteScreensPanel::setup() {
  ESP_LOGI(TAG, "Ready for a layout");
  this->register_service(&RemoteScreensPanel::on_set_layout, "set_layout", {"layout"});
  this->register_service(&RemoteScreensPanel::on_patch, "patch", {"patch"});
}

void RemoteScreensPanel::on_set_layout(std::string layout) {
  auto copy = std::make_shared<std::string>(std::move(layout));
  this->defer([this, copy]() { this->apply_layout_(*copy); });
}

void RemoteScreensPanel::on_patch(std::string patch) {
  auto copy = std::make_shared<std::string>(std::move(patch));
  this->defer([this, copy]() { this->apply_patch_(*copy); });
}

void RemoteScreensPanel::clear_screen_() {
  this->root_ = nullptr;
  this->widgets_.clear();
  this->taps_.clear();
  lv_obj_clean(active_screen());
}

void RemoteScreensPanel::apply_layout_(const std::string &layout) {
  if (layout.size() > MAX_LAYOUT_BYTES) {
    ESP_LOGW(TAG, "Layout is too large for the panel");
    return;
  }
  bool parsed = json::parse_json(layout, [this](JsonObject root) {
    int version = root["v"] | 0;
    if (version != PAYLOAD_VERSION) {
      ESP_LOGW(TAG, "Unknown layout version %d", version);
      return true;
    }
    if (!root["screen"].isNull()) this->screen_id_ = root["screen"].as<std::string>();
    if (!root["page"].isNull()) this->page_id_ = root["page"].as<std::string>();
    lock_lvgl();
    this->clear_screen_();
    lv_obj_set_style_bg_color(active_screen(), color_of(root["bg"] | "000000"), 0);
    lv_obj_set_style_bg_opa(active_screen(), LV_OPA_COVER, 0);
    JsonArray widgets = root["widgets"].as<JsonArray>();
    if (widgets.size() > 0) this->root_ = this->build_(active_screen(), widgets[0]);
    unlock_lvgl();
    return true;
  });
  if (!parsed) ESP_LOGW(TAG, "Layout is not JSON");
}

void RemoteScreensPanel::apply_patch_(const std::string &patch) {
  bool parsed = json::parse_json(patch, [this](JsonObject root) {
    lock_lvgl();
    for (JsonObject item : root["widgets"].as<JsonArray>()) {
      std::string id = item["id"] | "";
      auto found = this->widgets_.find(id);
      if (found == this->widgets_.end()) continue;
      lv_obj_t *obj = found->second.obj;
      if (!item["text"].isNull()) lv_label_set_text(obj, item["text"].as<const char *>());
      if (!item["color"].isNull()) lv_obj_set_style_text_color(obj, color_of(item["color"]), 0);
      if (item["value"].isNull()) continue;
      int value = item["value"].as<int>();
      const std::string &kind = found->second.kind;
      if (kind == "switch") {
        if (value != 0) {
          lv_obj_add_state(obj, LV_STATE_CHECKED);
        } else {
          lv_obj_clear_state(obj, LV_STATE_CHECKED);
        }
      } else if (kind == "bar") {
        lv_bar_set_value(obj, value, LV_ANIM_OFF);
      } else if (kind == "slider") {
        lv_slider_set_value(obj, value, LV_ANIM_OFF);
      } else if (kind == "arc") {
        lv_arc_set_value(obj, value);
      }
    }
    unlock_lvgl();
    return true;
  });
  if (!parsed) ESP_LOGW(TAG, "Patch is not JSON");
}

lv_obj_t *RemoteScreensPanel::build_(lv_obj_t *parent, JsonObject widget) {
  const char *kind = widget["k"] | "column";
  const char *id = widget["id"] | "";
  lv_obj_t *obj = nullptr;
  bool walk_children = true;

  if (std::strcmp(kind, "text") == 0 || std::strcmp(kind, "icon") == 0 || std::strcmp(kind, "image") == 0 ||
      std::strcmp(kind, "qr") == 0) {
    obj = lv_label_create(parent);
    lv_label_set_text(obj, widget["text"] | "");
  } else if (std::strcmp(kind, "button") == 0) {
    obj = make_button(parent);
    lv_obj_t *label = lv_label_create(obj);
    lv_label_set_text(label, widget["text"] | "");
    if (id[0] != '\0') this->widgets_[id] = WidgetRef{label, "text"};
    walk_children = false;
  } else if (std::strcmp(kind, "switch") == 0) {
    obj = lv_switch_create(parent);
    if ((widget["value"] | 0) != 0) lv_obj_add_state(obj, LV_STATE_CHECKED);
  } else if (std::strcmp(kind, "slider") == 0) {
    obj = lv_slider_create(parent);
    lv_slider_set_range(obj, widget["min"] | 0, widget["max"] | 100);
    lv_slider_set_value(obj, widget["value"] | 0, LV_ANIM_OFF);
  } else if (std::strcmp(kind, "bar") == 0) {
    obj = lv_bar_create(parent);
    lv_bar_set_range(obj, widget["min"] | 0, widget["max"] | 100);
    lv_bar_set_value(obj, widget["value"] | 0, LV_ANIM_OFF);
  } else if (std::strcmp(kind, "arc") == 0) {
    obj = lv_arc_create(parent);
    lv_arc_set_range(obj, widget["min"] | 0, widget["max"] | 100);
    lv_arc_set_value(obj, widget["value"] | 0);
  } else if (std::strcmp(kind, "tabs") == 0) {
    obj = make_tabs(parent);
    walk_children = false;
    int index = 0;
    JsonArray labels = widget["tabs"].as<JsonArray>();
    for (JsonObject child : widget["c"].as<JsonArray>()) {
      const char *name = "Tab";
      if (index < static_cast<int>(labels.size())) name = labels[index].as<const char *>();
      this->build_(lv_tabview_add_tab(obj, name), child);
      index += 1;
    }
  } else {
    obj = lv_obj_create(parent);
    if (std::strcmp(kind, "column") == 0 || std::strcmp(kind, "row") == 0 || std::strcmp(kind, "box") == 0 ||
        std::strcmp(kind, "flow") == 0) {
      flex_flow(obj, kind);
    }
    if ((widget["scroll"] | false) == false) lv_obj_clear_flag(obj, LV_OBJ_FLAG_SCROLLABLE);
  }

  apply_chrome(obj, widget);
  if (std::strcmp(kind, "button") != 0 && id[0] != '\0') this->widgets_[id] = WidgetRef{obj, kind};
  if (widget["tap"] | false) this->add_tap_(obj, id);
  if (walk_children) {
    for (JsonObject child : widget["c"].as<JsonArray>()) this->build_(obj, child);
  }
  return obj;
}

void RemoteScreensPanel::add_tap_(lv_obj_t *obj, const std::string &node_id) {
  auto tap = std::make_unique<TapBinding>();
  tap->panel = this;
  tap->node_id = node_id;
  lv_obj_add_event_cb(obj, RemoteScreensPanel::on_click_, LV_EVENT_CLICKED, tap.get());
  this->taps_.push_back(std::move(tap));
}

void RemoteScreensPanel::on_click_(lv_event_t *event) {
  auto *tap = static_cast<TapBinding *>(lv_event_get_user_data(event));
  tap->panel->pending_tap_ = tap->node_id;
  tap->panel->defer([panel = tap->panel]() { panel->send_tap_(); });
}

void RemoteScreensPanel::send_tap_() {
  if (this->pending_tap_.empty() || !this->is_connected()) return;
  std::string node_id = this->pending_tap_;
  this->pending_tap_.clear();
  this->call_homeassistant_service("remote_screens.panel_tap",
                                   {{"screen_id", this->screen_id_},
                                    {"page_id", this->page_id_},
                                    {"node_id", node_id}});
}

}  // namespace remote_screens_panel
}  // namespace esphome
