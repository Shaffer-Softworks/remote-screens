idf_component_register(
    SRCS "main.cpp" "board_4b.c"
    INCLUDE_DIRS "."
    REQUIRES panel nvs_flash esp_wifi esp_event esp_netif mdns esp_websocket_client
             esp_lcd esp_driver_gpio esp_driver_ledc esp_driver_i2c esp_hw_support esp_mm
)
