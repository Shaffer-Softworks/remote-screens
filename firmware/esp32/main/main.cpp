#include "board_4b.h"
#include "panel_player.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <string>
#include <tuple>
#include <vector>

#include "esp_event.h"
#include "esp_heap_caps.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_random.h"
#include "esp_websocket_client.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "mdns.h"
#include "nvs.h"
#include "nvs_flash.h"

namespace {

constexpr char TAG[] = "panel";
constexpr int PANEL_DENSITY_NUMERATOR = 1;
constexpr int SEARCH_TIMEOUT_MS = 3000;
constexpr int RECONNECT_DELAY_MS = 2000;
constexpr int FRAME_BYTES_PER_PIXEL = 4;
constexpr int MESSAGE_CAP = 256 * 1024;
constexpr uint32_t INK = 0xFFFFFFFFu;
constexpr int PAIR_SCALE = 4;
constexpr int SHOT_STEP = 4;
constexpr int SHOT_FALLBACK_LOOPS = 12;
constexpr uint32_t BYTE_MASK = 0xFFu;
constexpr int TOKEN_CAP = 64;
constexpr int PAINT_STACK_BYTES = 15360;
constexpr char TOKEN_KEY[] = "token";

struct PaintJob {
    bool hasDocument = false;
    std::vector<uint8_t> bytes;
    int revision = 0;
    std::vector<std::tuple<std::string, std::string, std::string>> vars;
};

struct Link {
    esp_websocket_client_handle_t socket = nullptr;
    std::string incoming;
    std::string deviceId;
    std::string token;
    int revision = 0;
    bool adopted = false;
    PanelDocument document;
    uint32_t* pixels = nullptr;
    int width = CONFIG_PANEL_WIDTH;
    int height = CONFIG_PANEL_HEIGHT;
};

Link session;
PaintJob queued;
SemaphoreHandle_t paintLock = nullptr;
TaskHandle_t paintHandle = nullptr;

extern "C" void __attribute__((weak)) panel_present_frame(
    const uint32_t* pixels,
    int x,
    int y,
    int width,
    int height,
    int stride
) {
    (void)pixels;
    (void)x;
    (void)y;
    (void)width;
    (void)height;
    (void)stride;
}

extern "C" bool __attribute__((weak)) panel_read_touch(int* x, int* y, bool* down) {
    (void)x;
    (void)y;
    (void)down;
    return false;
}

void present(const PanelRect& rect) {
    int x = std::max(0, rect.x);
    int y = std::max(0, rect.y);
    int width = std::min(session.width - x, rect.w);
    int height = std::min(session.height - y, rect.h);
    if (width <= 0 || height <= 0) {
        x = 0;
        y = 0;
        width = session.width;
        height = session.height;
    }
    panel_present_frame(session.pixels, x, y, width, height, session.width);
}

void showPairing(const std::string& code) {
    std::fill(session.pixels, session.pixels + static_cast<size_t>(session.width * session.height), 0xFF101418u);
    std::string line = code.empty() ? "PAIR" : code;
    panelDrawText(session.pixels, session.width, session.height, session.width, line.c_str(), 16, session.height / 2, PAIR_SCALE, INK);
    present(PanelRect{0, 0, session.width, session.height});
}

void dumpShot() {
    static bool sent = false;
    if (sent || session.pixels == nullptr || session.width < SHOT_STEP || session.height < SHOT_STEP) return;
    sent = true;
    int shotW = session.width / SHOT_STEP;
    int shotH = session.height / SHOT_STEP;
    std::vector<uint8_t> rgb(static_cast<size_t>(shotW * shotH * 3));
    size_t cursor = 0;
    for (int y = 0; y < shotH * SHOT_STEP; y += SHOT_STEP) {
        for (int x = 0; x < shotW * SHOT_STEP; x += SHOT_STEP) {
            uint32_t pixel = session.pixels[static_cast<size_t>(y) * static_cast<size_t>(session.width) + static_cast<size_t>(x)];
            rgb[cursor++] = static_cast<uint8_t>(pixel & BYTE_MASK);
            rgb[cursor++] = static_cast<uint8_t>((pixel >> 8) & BYTE_MASK);
            rgb[cursor++] = static_cast<uint8_t>((pixel >> 16) & BYTE_MASK);
        }
    }
    esp_log_level_set("*", ESP_LOG_NONE);
    printf("FRAME_BEGIN %d %d\n", shotW, shotH);
    fwrite(rgb.data(), 1, rgb.size(), stdout);
    printf("\nFRAME_END\n");
    fflush(stdout);
    esp_log_level_set("*", ESP_LOG_INFO);
}

void showDocument() {
    session.document.layout();
    PanelFrame frame;
    std::string font = "/fonts/Roboto-Regular.ttf";
    if (!panelPaint(&session.document, session.pixels, session.width, session.height, font.c_str(), &frame)) {
        ESP_LOGE(TAG, "paint failed");
        return;
    }
    ESP_LOGI(TAG, "document %d bytes, frame %d us, dirty %d,%d %dx%d", frame.documentBytes, frame.microseconds, frame.dirty.x, frame.dirty.y, frame.dirty.w, frame.dirty.h);
    present(frame.dirty);
    dumpShot();
}

void saveToken(const std::string& token) {
    if (token.empty() || token.size() >= TOKEN_CAP) return;
    nvs_handle_t store;
    if (nvs_open("panel", NVS_READWRITE, &store) != ESP_OK) return;
    nvs_set_str(store, TOKEN_KEY, token.c_str());
    nvs_commit(store);
    nvs_close(store);
    ESP_LOGI(TAG, "pairing token saved");
}

std::string loadToken() {
    nvs_handle_t store;
    char existing[TOKEN_CAP] = {};
    size_t length = sizeof(existing);
    if (nvs_open("panel", NVS_READONLY, &store) != ESP_OK) return {};
    esp_err_t result = nvs_get_str(store, TOKEN_KEY, existing, &length);
    nvs_close(store);
    if (result != ESP_OK) return {};
    return existing;
}

void enqueue(PaintJob job) {
    if (paintLock == nullptr) return;
    xSemaphoreTake(paintLock, portMAX_DELAY);
    if (job.hasDocument) {
        queued.hasDocument = true;
        queued.bytes = std::move(job.bytes);
        queued.revision = job.revision;
        queued.vars.clear();
    }
    queued.vars.insert(queued.vars.end(), job.vars.begin(), job.vars.end());
    xSemaphoreGive(paintLock);
    if (paintHandle != nullptr) xTaskNotifyGive(paintHandle);
}

void paintTask(void* arg) {
    (void)arg;
    while (true) {
        ulTaskNotifyTake(pdTRUE, portMAX_DELAY);
        PaintJob job;
        xSemaphoreTake(paintLock, portMAX_DELAY);
        job = std::move(queued);
        queued = {};
        xSemaphoreGive(paintLock);
        bool ready = false;
        if (job.hasDocument) {
            std::string error;
            if (!session.document.load(job.bytes.data(), job.bytes.size(), &error)) {
                ESP_LOGE(TAG, "document rejected: %s", error.c_str());
            } else {
                session.revision = job.revision;
                session.adopted = true;
                ready = true;
            }
        }
        bool changed = false;
        for (const auto& var : job.vars) {
            changed = session.document.setVar(std::get<0>(var), std::get<1>(var), std::get<2>(var)) || changed;
        }
        if (ready || changed) showDocument();
    }
}

void onMessage(const std::string& json) {
    std::string type;
    std::string pairingCode;
    std::string slot;
    int revision = 0;
    std::string encoded;
    std::string token;
    std::vector<std::tuple<std::string, std::string, std::string>> vars;
    if (!panelTakeMessage(json, &type, &pairingCode, &slot, &revision, &encoded, &vars, &token)) return;
    if (type == "welcome") {
        if (!token.empty()) {
            session.token = token;
            saveToken(token);
        }
        session.adopted = pairingCode.empty();
        if (!session.adopted) showPairing(pairingCode);
        return;
    }
    if (type == "document" && slot == "main") {
        PaintJob job;
        if (!panelDecodeBase64(encoded, &job.bytes)) {
            ESP_LOGE(TAG, "document rejected: bad encoding");
            return;
        }
        job.hasDocument = true;
        job.revision = revision;
        enqueue(job);
        return;
    }
    if (type == "vars") {
        PaintJob job;
        job.vars = std::move(vars);
        enqueue(job);
    }
}

void websocketEvent(void* arg, esp_event_base_t base, int32_t id, void* data) {
    (void)arg;
    (void)base;
    auto* event = static_cast<esp_websocket_event_data_t*>(data);
    if (id == WEBSOCKET_EVENT_CONNECTED) {
        std::string hello = panelHello(
            session.deviceId,
            "Panel",
            session.width,
            session.height,
            PANEL_DENSITY_NUMERATOR,
            session.token
        );
        ESP_LOGI(TAG, "hello token %s", session.token.empty() ? "absent" : "present");
        esp_websocket_client_send_text(session.socket, hello.c_str(), static_cast<int>(hello.size()), portMAX_DELAY);
        return;
    }
    if (id != WEBSOCKET_EVENT_DATA || event == nullptr || event->op_code != 0x1) return;
    if (event->payload_offset == 0) session.incoming.clear();
    session.incoming.append(event->data_ptr, static_cast<size_t>(event->data_len));
    if (event->payload_offset + event->data_len >= event->payload_len) {
        onMessage(session.incoming);
        session.incoming.clear();
    }
}

std::string deviceId() {
    nvs_handle_t store;
    char existing[33] = {};
    size_t length = sizeof(existing);
    if (nvs_open("panel", NVS_READWRITE, &store) != ESP_OK) return "panel";
    if (nvs_get_str(store, "device_id", existing, &length) == ESP_OK && existing[0] != '\0') {
        nvs_close(store);
        return existing;
    }
    char created[17];
    for (int index = 0; index < 16; index++) {
        created[index] = "0123456789abcdef"[esp_random() % 16];
    }
    created[16] = '\0';
    nvs_set_str(store, "device_id", created);
    nvs_commit(store);
    nvs_close(store);
    return created;
}

bool discover(std::string* uri) {
    mdns_result_t* results = nullptr;
    if (mdns_query_ptr("_remotescreens", "_tcp", SEARCH_TIMEOUT_MS, 4, &results) != ESP_OK || results == nullptr) {
        return false;
    }
    bool found = false;
    for (mdns_result_t* result = results; result != nullptr && !found; result = result->next) {
        if (result->addr == nullptr) continue;
        char host[16];
        snprintf(host, sizeof(host), IPSTR, IP2STR(&result->addr->addr.u_addr.ip4));
        *uri = std::string("ws://") + host + ":" + std::to_string(result->port) + "/player";
        found = true;
    }
    mdns_query_results_free(results);
    return found;
}

void connect(const std::string& uri) {
    if (session.socket != nullptr) {
        esp_websocket_client_destroy(session.socket);
        session.socket = nullptr;
    }
    esp_websocket_client_config_t config = {};
    config.uri = uri.c_str();
    config.buffer_size = MESSAGE_CAP;
    session.socket = esp_websocket_client_init(&config);
    esp_websocket_register_events(session.socket, WEBSOCKET_EVENT_ANY, websocketEvent, nullptr);
    esp_websocket_client_start(session.socket);
}

void touchTask(void* arg) {
    (void)arg;
    bool wasDown = false;
    while (true) {
        int x = 0;
        int y = 0;
        bool down = false;
        if (panel_read_touch(&x, &y, &down) && down && !wasDown && session.adopted && session.socket != nullptr) {
            std::string action = session.document.hit(static_cast<float>(x), static_cast<float>(y));
            if (!action.empty()) {
                std::string message = panelAction(session.revision, action);
                esp_websocket_client_send_text(session.socket, message.c_str(), static_cast<int>(message.size()), portMAX_DELAY);
            }
        }
        wasDown = down;
        vTaskDelay(pdMS_TO_TICKS(30));
    }
}

}  // namespace

extern "C" void app_main(void) {
    esp_err_t nvs = nvs_flash_init();
    if (nvs == ESP_ERR_NVS_NO_FREE_PAGES || nvs == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        nvs_flash_erase();
        nvs_flash_init();
    }
    board_bringup();
    session.deviceId = deviceId();
    session.token = loadToken();
    size_t pixels = static_cast<size_t>(session.width) * static_cast<size_t>(session.height);
    session.pixels = static_cast<uint32_t*>(heap_caps_malloc(pixels * FRAME_BYTES_PER_PIXEL, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT));
    if (session.pixels == nullptr) {
        session.pixels = static_cast<uint32_t*>(heap_caps_malloc(pixels * FRAME_BYTES_PER_PIXEL, MALLOC_CAP_8BIT));
    }
    if (session.pixels == nullptr) {
        ESP_LOGE(TAG, "no framebuffer");
        return;
    }
    showPairing("");
    extern const uint8_t font_start[] asm("_binary_Roboto_Regular_ttf_start");
    extern const uint8_t font_end[] asm("_binary_Roboto_Regular_ttf_end");
    panelSetFont(font_start, static_cast<size_t>(font_end - font_start));
    paintLock = xSemaphoreCreateMutex();
    xTaskCreate(paintTask, "paint", PAINT_STACK_BYTES, nullptr, 4, &paintHandle);
    esp_netif_init();
    esp_event_loop_create_default();
    esp_netif_create_default_wifi_sta();
    wifi_init_config_t wifi = WIFI_INIT_CONFIG_DEFAULT();
    esp_wifi_init(&wifi);
    esp_wifi_set_mode(WIFI_MODE_STA);
    wifi_config_t station = {};
    std::strncpy(reinterpret_cast<char*>(station.sta.ssid), CONFIG_PANEL_WIFI_SSID, sizeof(station.sta.ssid) - 1);
    std::strncpy(reinterpret_cast<char*>(station.sta.password), CONFIG_PANEL_WIFI_PASSWORD, sizeof(station.sta.password) - 1);
    esp_wifi_set_config(WIFI_IF_STA, &station);
    esp_wifi_start();
    esp_wifi_connect();
    mdns_init();
    xTaskCreate(touchTask, "touch", 4096, nullptr, 4, nullptr);
    int idleLoops = 0;
    while (true) {
        bool connected = session.socket != nullptr && esp_websocket_client_is_connected(session.socket);
        if (!connected) {
            std::string uri;
            if (discover(&uri)) {
                ESP_LOGI(TAG, "add-on %s", uri.c_str());
                connect(uri);
            }
        }
        if (++idleLoops == SHOT_FALLBACK_LOOPS) dumpShot();
        vTaskDelay(pdMS_TO_TICKS(RECONNECT_DELAY_MS));
    }
}
