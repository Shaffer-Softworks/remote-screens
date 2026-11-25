# ESP32 panel

A player for one clock-and-tiles page. It discovers the add-on as `_remotescreens._tcp` on port 8100, says hello with the `panel` capability, and draws the pairing code until a document arrives. Wi-Fi credentials stay on the device. The Home Assistant token does not.

The painter is upstream ThorVG v1.1.4 (MIT), fetched and not committed:

```sh
git clone --depth 1 --branch v1.1.4 https://github.com/thorvg/thorvg.git firmware/esp32/third_party/thorvg
```

Host check, without a board or ESP-IDF. It decodes `testdata/panel.rc`, paints it, and prints the document size and frame time:

```sh
cmake -S firmware/esp32/host -B firmware/esp32/host/build
cmake --build firmware/esp32/host/build
./firmware/esp32/host/build/panel_host
```

The first board is the Waveshare ESP32-P4-WIFI6-Touch-LCD-4B: a 720×720 MIPI panel, GT911 touch, and Wi-Fi from the onboard ESP32-C6. `main/board_4b.c` paints the framebuffer and reads touch. From `firmware/esp32`, with ESP-IDF loaded:

```sh
idf.py set-target esp32p4
idf.py menuconfig   # Panel: Wi-Fi, width, height
idf.py build flash monitor
```

`fonts/Roboto-Regular.ttf` is Roboto, Apache 2.0. The board image embeds it. The pairing code uses the built-in glyph table and does not need the file.
