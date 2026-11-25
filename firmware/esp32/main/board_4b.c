#include "sdkconfig.h"

#if CONFIG_IDF_TARGET_ESP32P4

#include "board_4b.h"

#include <string.h>

#include "driver/gpio.h"
#include "driver/i2c_master.h"
#include "driver/ledc.h"
#include "esp_cache.h"
#include "esp_check.h"
#include "esp_heap_caps.h"
#include "esp_lcd_mipi_dsi.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_st7703.h"
#include "esp_lcd_touch_gt911.h"
#include "esp_ldo_regulator.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#define TAG "panel-board"

#define PANEL_WIDTH 720
#define PANEL_HEIGHT 720
#define LANE_BITRATE_MBPS 480
#define DSI_LANES 2
#define MIPI_LDO_CHAN 3
#define MIPI_LDO_MV 2500
#define IO_LDO_CHAN 4
#define IO_LDO_MV 3300
#define PIN_LCD_RST GPIO_NUM_27
#define PIN_BACKLIGHT GPIO_NUM_26
#define PIN_BACKLIGHT_EN GPIO_NUM_33
#define PIN_TOUCH_RST GPIO_NUM_23
#define PIN_SDA GPIO_NUM_7
#define PIN_SCL GPIO_NUM_8
#define TOUCH_PRIMARY 0x5D
#define TOUCH_BACKUP 0x14
#define BACKLIGHT_CHANNEL LEDC_CHANNEL_1
#define BACKLIGHT_TIMER LEDC_TIMER_1
#define BACKLIGHT_BITS LEDC_TIMER_10_BIT
#define BACKLIGHT_FULL 1023
#define RGB565_RED_SHIFT 11
#define RGB565_GREEN_SHIFT 5
#define BYTE_MASK 0xFF
#define RED_5BIT_SHIFT 3
#define GREEN_6BIT_SHIFT 2
#define BLUE_5BIT_SHIFT 3
#define RESET_LOW_MS 10
#define RESET_HIGH_MS 50
#define I2C_HZ 400000
#define CACHE_ALIGN 64

static esp_lcd_panel_handle_t panel;
static esp_lcd_touch_handle_t touch;
static uint16_t* rgb565;

static esp_err_t power_rails(void) {
    esp_ldo_channel_handle_t mipi = NULL;
    esp_ldo_channel_handle_t io = NULL;
    const esp_ldo_channel_config_t mipi_cfg = {
        .chan_id = MIPI_LDO_CHAN,
        .voltage_mv = MIPI_LDO_MV,
    };
    const esp_ldo_channel_config_t io_cfg = {
        .chan_id = IO_LDO_CHAN,
        .voltage_mv = IO_LDO_MV,
    };
    ESP_RETURN_ON_ERROR(esp_ldo_acquire_channel(&mipi_cfg, &mipi), TAG, "mipi ldo");
    return esp_ldo_acquire_channel(&io_cfg, &io);
}

static esp_err_t backlight(void) {
    gpio_config_t enable = {
        .pin_bit_mask = 1ULL << PIN_BACKLIGHT_EN,
        .mode = GPIO_MODE_OUTPUT,
    };
    ESP_RETURN_ON_ERROR(gpio_config(&enable), TAG, "backlight enable");
    ESP_RETURN_ON_ERROR(gpio_set_level(PIN_BACKLIGHT_EN, 1), TAG, "backlight level");

    const ledc_timer_config_t timer = {
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .duty_resolution = BACKLIGHT_BITS,
        .timer_num = BACKLIGHT_TIMER,
        .freq_hz = 5000,
        .clk_cfg = LEDC_AUTO_CLK,
    };
    const ledc_channel_config_t channel = {
        .gpio_num = PIN_BACKLIGHT,
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .channel = BACKLIGHT_CHANNEL,
        .intr_type = LEDC_INTR_DISABLE,
        .timer_sel = BACKLIGHT_TIMER,
        .duty = BACKLIGHT_FULL,
        .hpoint = 0,
        .flags.output_invert = 1,
    };
    ESP_RETURN_ON_ERROR(ledc_timer_config(&timer), TAG, "backlight timer");
    ESP_RETURN_ON_ERROR(ledc_channel_config(&channel), TAG, "backlight channel");
    ESP_RETURN_ON_ERROR(ledc_set_duty(LEDC_LOW_SPEED_MODE, BACKLIGHT_CHANNEL, BACKLIGHT_FULL), TAG, "backlight duty");
    return ledc_update_duty(LEDC_LOW_SPEED_MODE, BACKLIGHT_CHANNEL);
}

static esp_err_t display(void) {
    esp_lcd_dsi_bus_handle_t bus = NULL;
    const esp_lcd_dsi_bus_config_t bus_cfg = {
        .bus_id = 0,
        .num_data_lanes = DSI_LANES,
        .phy_clk_src = MIPI_DSI_PHY_CLK_SRC_DEFAULT,
        .lane_bit_rate_mbps = LANE_BITRATE_MBPS,
    };
    ESP_RETURN_ON_ERROR(esp_lcd_new_dsi_bus(&bus_cfg, &bus), TAG, "dsi bus");

    esp_lcd_panel_io_handle_t io = NULL;
    const esp_lcd_dbi_io_config_t dbi = {
        .virtual_channel = 0,
        .lcd_cmd_bits = 8,
        .lcd_param_bits = 8,
    };
    ESP_RETURN_ON_ERROR(esp_lcd_new_panel_io_dbi(bus, &dbi, &io), TAG, "dbi");

    esp_lcd_dpi_panel_config_t dpi = ST7703_720_720_PANEL_60HZ_DPI_CONFIG(LCD_COLOR_PIXEL_FORMAT_RGB565);
    dpi.num_fbs = 1;
    st7703_vendor_config_t vendor = {
        .flags.use_mipi_interface = 1,
        .mipi_config = {
            .dsi_bus = bus,
            .dpi_config = &dpi,
        },
    };
    const esp_lcd_panel_dev_config_t dev = {
        .reset_gpio_num = PIN_LCD_RST,
        .rgb_ele_order = LCD_RGB_ELEMENT_ORDER_RGB,
        .bits_per_pixel = 16,
        .vendor_config = &vendor,
    };
    ESP_RETURN_ON_ERROR(esp_lcd_new_panel_st7703(io, &dev, &panel), TAG, "st7703");
    ESP_RETURN_ON_ERROR(esp_lcd_panel_reset(panel), TAG, "reset");
    ESP_RETURN_ON_ERROR(esp_lcd_panel_init(panel), TAG, "init");
    return esp_lcd_panel_disp_on_off(panel, true);
}

static esp_err_t touch_bus(i2c_master_bus_handle_t* bus, uint8_t* address) {
    gpio_config_t reset = {
        .pin_bit_mask = 1ULL << PIN_TOUCH_RST,
        .mode = GPIO_MODE_OUTPUT,
    };
    ESP_RETURN_ON_ERROR(gpio_config(&reset), TAG, "touch reset");
    gpio_set_level(PIN_TOUCH_RST, 0);
    vTaskDelay(pdMS_TO_TICKS(RESET_LOW_MS));
    gpio_set_level(PIN_TOUCH_RST, 1);
    vTaskDelay(pdMS_TO_TICKS(RESET_HIGH_MS));

    const i2c_master_bus_config_t cfg = {
        .i2c_port = I2C_NUM_0,
        .sda_io_num = PIN_SDA,
        .scl_io_num = PIN_SCL,
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };
    ESP_RETURN_ON_ERROR(i2c_new_master_bus(&cfg, bus), TAG, "i2c");
    if (i2c_master_probe(*bus, TOUCH_PRIMARY, RESET_HIGH_MS) == ESP_OK) {
        *address = TOUCH_PRIMARY;
        return ESP_OK;
    }
    if (i2c_master_probe(*bus, TOUCH_BACKUP, RESET_HIGH_MS) == ESP_OK) {
        *address = TOUCH_BACKUP;
        return ESP_OK;
    }
    return ESP_ERR_NOT_FOUND;
}

static esp_err_t touch_panel(void) {
    i2c_master_bus_handle_t bus = NULL;
    uint8_t address = TOUCH_PRIMARY;
    ESP_RETURN_ON_ERROR(touch_bus(&bus, &address), TAG, "touch address");

    esp_lcd_panel_io_handle_t io = NULL;
    esp_lcd_panel_io_i2c_config_t io_cfg = ESP_LCD_TOUCH_IO_I2C_GT911_CONFIG();
    io_cfg.dev_addr = address;
    io_cfg.scl_speed_hz = I2C_HZ;
    ESP_RETURN_ON_ERROR(esp_lcd_new_panel_io_i2c(bus, &io_cfg, &io), TAG, "touch io");

    const esp_lcd_touch_config_t cfg = {
        .x_max = PANEL_WIDTH,
        .y_max = PANEL_HEIGHT,
        .rst_gpio_num = GPIO_NUM_NC,
        .int_gpio_num = GPIO_NUM_NC,
        .levels.reset = 0,
        .flags.swap_xy = 0,
    };
    ESP_LOGI(TAG, "touch address 0x%02x", address);
    return esp_lcd_touch_new_i2c_gt911(io, &cfg, &touch);
}

void board_bringup(void) {
    if (power_rails() != ESP_OK || backlight() != ESP_OK || display() != ESP_OK) {
        ESP_LOGE(TAG, "display did not start");
        panel = NULL;
        return;
    }
    size_t bytes = (size_t)PANEL_WIDTH * (size_t)PANEL_HEIGHT * sizeof(uint16_t);
    rgb565 = heap_caps_aligned_alloc(CACHE_ALIGN, bytes, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (rgb565 == NULL) {
        ESP_LOGE(TAG, "no rgb565 buffer");
    }
    if (touch_panel() != ESP_OK) {
        ESP_LOGW(TAG, "touch did not start");
        touch = NULL;
    }
    ESP_LOGI(TAG, "Waveshare 4B %dx%d", PANEL_WIDTH, PANEL_HEIGHT);
}

void panel_present_frame(const uint32_t* pixels, int x, int y, int width, int height, int stride) {
    if (panel == NULL || rgb565 == NULL || pixels == NULL || width <= 0 || height <= 0) return;
    for (int row = 0; row < height; row++) {
        const uint32_t* src = pixels + (size_t)(y + row) * (size_t)stride + (size_t)x;
        uint16_t* dst = rgb565 + (size_t)row * (size_t)width;
        for (int col = 0; col < width; col++) {
            uint32_t pixel = src[col];
            uint16_t red = (uint16_t)((pixel & BYTE_MASK) >> RED_5BIT_SHIFT);
            uint16_t green = (uint16_t)(((pixel >> 8) & BYTE_MASK) >> GREEN_6BIT_SHIFT);
            uint16_t blue = (uint16_t)(((pixel >> 16) & BYTE_MASK) >> BLUE_5BIT_SHIFT);
            dst[col] = (uint16_t)((red << RGB565_RED_SHIFT) | (green << RGB565_GREEN_SHIFT) | blue);
        }
    }
    size_t bytes = (size_t)width * (size_t)height * sizeof(uint16_t);
    esp_cache_msync(rgb565, bytes, ESP_CACHE_MSYNC_FLAG_DIR_C2M | ESP_CACHE_MSYNC_FLAG_UNALIGNED);
    esp_lcd_panel_draw_bitmap(panel, x, y, x + width, y + height, rgb565);
}

bool panel_read_touch(int* x, int* y, bool* down) {
    if (touch == NULL) return false;
    esp_lcd_touch_point_data_t point = {0};
    uint8_t points = 0;
    esp_lcd_touch_read_data(touch);
    esp_lcd_touch_get_data(touch, &point, &points, 1);
    *x = point.x;
    *y = point.y;
    *down = points > 0;
    return true;
}

#else

void board_bringup(void) {}

#endif
