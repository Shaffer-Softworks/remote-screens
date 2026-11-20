#pragma once

#include "esphome/components/api/custom_api_device.h"
#include "esphome/components/json/json_util.h"
#include "esphome/core/component.h"
#include "lvgl.h"

#include <map>
#include <memory>
#include <string>
#include <vector>

namespace esphome {
namespace remote_screens_panel {

class RemoteScreensPanel;

struct TapBinding {
  RemoteScreensPanel *panel;
  std::string node_id;
};

struct WidgetRef {
  lv_obj_t *obj;
  std::string kind;
};

/** Draws a Remote Screens free-form page with LVGL and reports taps back to Home Assistant. */
class RemoteScreensPanel : public Component, public api::CustomAPIDevice {
 public:
  void setup() override;
  void set_screen_id(const std::string &screen_id) { this->screen_id_ = screen_id; }
  void set_page_id(const std::string &page_id) { this->page_id_ = page_id; }
  void on_set_layout(std::string layout);
  void on_patch(std::string patch);

 protected:
  void apply_layout_(const std::string &layout);
  void apply_patch_(const std::string &patch);
  void send_tap_();
  void clear_screen_();
  lv_obj_t *build_(lv_obj_t *parent, JsonObject widget);
  void add_tap_(lv_obj_t *obj, const std::string &node_id);
  static void on_click_(lv_event_t *event);

  std::string screen_id_;
  std::string page_id_;
  std::string pending_tap_;
  lv_obj_t *root_{nullptr};
  std::map<std::string, WidgetRef> widgets_;
  std::vector<std::unique_ptr<TapBinding>> taps_;
};

}  // namespace remote_screens_panel
}  // namespace esphome
