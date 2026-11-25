#pragma once

#include <cstdint>
#include <string>
#include <tuple>
#include <unordered_map>
#include <vector>

struct PanelRect {
    int x = 0;
    int y = 0;
    int w = 0;
    int h = 0;
};

struct PanelFrame {
    int milliseconds = 0;
    int microseconds = 0;
    PanelRect dirty;
    int documentBytes = 0;
};

struct PanelNode {
    int op = 0;
    int id = 0;
    std::vector<PanelNode> children;
    int widthType = -1;
    float widthValue = 0;
    int heightType = -1;
    float heightValue = 0;
    float padLeft = 0;
    float padTop = 0;
    float padRight = 0;
    float padBottom = 0;
    bool hasBackground = false;
    bool backgroundIsRef = false;
    int backgroundColorId = 0;
    uint32_t backgroundArgb = 0xFF000000;
    float radius = 0;
    bool hasVisibility = false;
    int visibilityId = 0;
    int textId = 0;
    float fontSize = 16;
    uint32_t textColor = 0xFF000000;
    int textColorId = -1;
    int align = 1;
    int indexId = 0;
    float spacedBy = 0;
    int alignH = 1;
    int alignV = 4;
    std::string action;
    int pathId = 0;
    float anchorX = 0;
    float anchorY = 0;
    float panX = -1;
    float panY = -1;
    uint32_t paintColor = 0xFF000000;
    int paintColorId = -1;
    bool hasPaint = false;
    bool paintIsRef = false;
    float x = 0;
    float y = 0;
    float w = 0;
    float h = 0;
    bool shown = true;
    bool takesSpace = true;
};

struct PanelDocument {
    int width = 0;
    int height = 0;
    float density = 1;
    int byteSize = 0;
    std::vector<PanelNode> roots;
    std::unordered_map<int, std::string> texts;
    std::unordered_map<int, uint32_t> colors;
    std::unordered_map<int, int> ints;
    std::unordered_map<int, float> floats;
    std::unordered_map<int, std::vector<float>> paths;
    std::unordered_map<std::string, int> names;
    std::unordered_map<int, int> nameTypes;
    PanelRect dirty{0, 0, 0, 0};

    bool load(const uint8_t* bytes, size_t size, std::string* error);
    bool setVar(const std::string& name, const std::string& type, const std::string& value);
    void layout();
    std::string hit(float x, float y) const;
    const std::string* text(int id) const;
    uint32_t colorOf(int id, uint32_t fallback) const;
};

void panelSetFont(const uint8_t* bytes, size_t size);

bool panelPaint(
    PanelDocument* document,
    uint32_t* pixels,
    int width,
    int height,
    const char* fontPath,
    PanelFrame* frame
);

void panelDrawText(
    uint32_t* pixels,
    int width,
    int height,
    int stride,
    const char* text,
    int originX,
    int originY,
    int scale,
    uint32_t abgr
);

std::string panelHello(
    const std::string& deviceId,
    const std::string& deviceName,
    int widthDp,
    int heightDp,
    float density,
    const std::string& token = ""
);

std::string panelAction(int revision, const std::string& metadata);

bool panelTakeMessage(
    const std::string& json,
    std::string* type,
    std::string* pairingCode,
    std::string* slot,
    int* revision,
    std::string* documentBase64,
    std::vector<std::tuple<std::string, std::string, std::string>>* vars,
    std::string* token = nullptr
);

bool panelDecodeBase64(const std::string& text, std::vector<uint8_t>* out);
