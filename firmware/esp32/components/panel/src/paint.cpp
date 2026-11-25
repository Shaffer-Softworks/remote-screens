#include "panel_player.h"

#include <thorvg.h>

#include <chrono>
#include <cstring>
#include <vector>

namespace {

constexpr int CHANNEL_SHIFT_RED = 16;
constexpr int CHANNEL_SHIFT_GREEN = 8;
constexpr int CHANNEL_MASK = 0xFF;
constexpr float CENTER_ALIGN = 3.f;
constexpr int ALIGN_CENTER = 3;
constexpr int ALIGN_RIGHT = 2;
constexpr float TEXT_WIDTH_FACTOR = 0.55f;

struct Channels {
    uint8_t red;
    uint8_t green;
    uint8_t blue;
    uint8_t alpha;
};

Channels channelsOf(uint32_t argb) {
    return Channels{
        static_cast<uint8_t>((argb >> CHANNEL_SHIFT_RED) & CHANNEL_MASK),
        static_cast<uint8_t>((argb >> CHANNEL_SHIFT_GREEN) & CHANNEL_MASK),
        static_cast<uint8_t>(argb & CHANNEL_MASK),
        static_cast<uint8_t>((argb >> 24) & CHANNEL_MASK),
    };
}

bool command(float value, int* id) {
    uint32_t bits = 0;
    std::memcpy(&bits, &value, sizeof(bits));
    if ((bits & 0x7f800000u) != 0x7f800000u) return false;
    *id = static_cast<int>(bits & 0x3fffffu);
    return *id >= 10 && *id <= 16;
}

constexpr int PATH_MOVE = 10;
constexpr int PATH_LINE = 11;
constexpr int PATH_QUADRATIC = 12;
constexpr int PATH_CONIC = 13;
constexpr int PATH_CUBIC = 14;
constexpr int PATH_CLOSE = 15;
constexpr int PATH_START_SLOT = 2;
constexpr float DEFAULT_TEXT_SIZE = 16.f;

float coordinate(const std::vector<float>& points, size_t* index) {
    if (*index >= points.size()) return 0;
    return points[(*index)++];
}

void skipStart(const std::vector<float>& points, size_t* index) {
    for (int slot = 0; slot < PATH_START_SLOT; slot++) coordinate(points, index);
}

uint32_t inkOf(uint32_t argb) {
    if (((argb >> 24) & CHANNEL_MASK) == 0) return 0xFFFFFFFFu;
    return argb;
}

void addPath(tvg::Shape* shape, const std::vector<float>& points, float originX, float originY) {
    size_t index = 0;
    while (index < points.size()) {
        int id = 0;
        if (!command(points[index], &id)) {
            index++;
            continue;
        }
        index++;
        if (id == PATH_MOVE) {
            float x = coordinate(points, &index);
            float y = coordinate(points, &index);
            shape->moveTo(originX + x, originY + y);
        } else if (id == PATH_LINE) {
            skipStart(points, &index);
            float x = coordinate(points, &index);
            float y = coordinate(points, &index);
            shape->lineTo(originX + x, originY + y);
        } else if (id == PATH_QUADRATIC) {
            skipStart(points, &index);
            float cx = coordinate(points, &index);
            float cy = coordinate(points, &index);
            float x = coordinate(points, &index);
            float y = coordinate(points, &index);
            shape->lineTo(originX + cx, originY + cy);
            shape->lineTo(originX + x, originY + y);
        } else if (id == PATH_CONIC) {
            skipStart(points, &index);
            for (int slot = 0; slot < 5; slot++) coordinate(points, &index);
        } else if (id == PATH_CUBIC) {
            skipStart(points, &index);
            float c1x = coordinate(points, &index);
            float c1y = coordinate(points, &index);
            float c2x = coordinate(points, &index);
            float c2y = coordinate(points, &index);
            float x = coordinate(points, &index);
            float y = coordinate(points, &index);
            shape->cubicTo(originX + c1x, originY + c1y, originX + c2x, originY + c2y, originX + x, originY + y);
        } else if (id == PATH_CLOSE) {
            shape->close();
        } else {
            break;
        }
    }
}

float estimatedWidth(const PanelDocument* document, const PanelNode* node) {
    const std::string* text = document->text(node->textId);
    size_t letters = text == nullptr ? 1 : std::max<size_t>(1, text->size());
    return static_cast<float>(letters) * node->fontSize * TEXT_WIDTH_FACTOR;
}

void addText(tvg::Canvas* canvas, const char* words, float size, uint32_t argb, float x, float y) {
    if (words == nullptr || words[0] == '\0') return;
    auto text = tvg::Text::gen();
    text->font("Roboto");
    text->size(std::max(1.f, size));
    text->text(words);
    Channels color = channelsOf(argb);
    text->fill(color.red, color.green, color.blue);
    text->translate(x, y);
    canvas->add(text);
}

void paintNode(
    tvg::Canvas* canvas,
    const PanelDocument* document,
    const PanelNode* node,
    float originX,
    float originY,
    uint32_t* paintColor,
    float* textSize
) {
    if (!node->shown && node->op != 40 && node->op != 123 && node->op != 124 && node->op != 133) return;
    float nextX = originX;
    float nextY = originY;
    if (node->w > 0 && node->h > 0 && node->op != 40) {
        nextX = node->x;
        nextY = node->y;
    }
    if (node->hasBackground && node->w > 1.f && node->h > 1.f) {
        auto shape = tvg::Shape::gen();
        float radius = std::max(0.f, node->radius);
        shape->appendRect(node->x, node->y, node->w, node->h, radius, radius);
        uint32_t argb = node->backgroundIsRef ? document->colorOf(node->backgroundColorId, node->backgroundArgb) : node->backgroundArgb;
        Channels color = channelsOf(argb);
        shape->fill(color.red, color.green, color.blue, color.alpha == 0 ? CHANNEL_MASK : color.alpha);
        canvas->add(shape);
    }
    if (node->op == 239) {
        const std::string* words = document->text(node->textId);
        uint32_t argb = inkOf(node->textColorId > 0 ? document->colorOf(node->textColorId, node->textColor) : node->textColor);
        float width = estimatedWidth(document, node);
        float left = node->x;
        if (node->align == ALIGN_CENTER) left = node->x + (node->w - width) / 2.f;
        if (node->align == ALIGN_RIGHT) left = node->x + node->w - width;
        float top = node->y + std::max(0.f, (node->h - node->fontSize) / 2.f);
        addText(canvas, words == nullptr ? "" : words->c_str(), node->fontSize, argb, left, top);
    }
    if (node->op == 40 && node->hasPaint) {
        *paintColor = inkOf(node->paintIsRef ? document->colorOf(node->paintColorId, 0xFFFFFFFFu) : node->paintColor);
        if (node->fontSize > 0.f) *textSize = node->fontSize;
    }
    if (node->op == 124) {
        auto found = document->paths.find(node->pathId);
        if (found != document->paths.end()) {
            auto shape = tvg::Shape::gen();
            addPath(shape, found->second, originX, originY);
            Channels color = channelsOf(inkOf(*paintColor));
            shape->fill(color.red, color.green, color.blue, color.alpha == 0 ? CHANNEL_MASK : color.alpha);
            canvas->add(shape);
        }
    }
    if (node->op == 133) {
        const std::string* words = document->text(node->textId);
        float size = *textSize > 0.f ? *textSize : DEFAULT_TEXT_SIZE;
        float letters = words == nullptr ? 1.f : static_cast<float>(std::max<size_t>(1, words->size()));
        float width = letters * size * TEXT_WIDTH_FACTOR;
        float left = originX + node->anchorX - width * (node->panX + 1.f) / 2.f;
        float top = originY + node->anchorY - size * (node->panY + 1.f) / CENTER_ALIGN;
        addText(canvas, words == nullptr ? "" : words->c_str(), size, inkOf(*paintColor), left, top);
    }
    for (const PanelNode& child : node->children) {
        paintNode(canvas, document, &child, nextX, nextY, paintColor, textSize);
    }
}

const uint8_t* fontBytes = nullptr;
size_t fontSize = 0;

bool ensureEngine(const char* fontPath) {
    static bool ready = false;
    static bool fontReady = false;
    if (!ready) {
        if (tvg::Initializer::init(0) != tvg::Result::Success) return false;
        ready = true;
    }
    if (!fontReady && fontBytes != nullptr && fontSize > 0) {
        fontReady = tvg::Text::load("Roboto", reinterpret_cast<const char*>(fontBytes), static_cast<uint32_t>(fontSize), "ttf", true) == tvg::Result::Success;
    }
    if (!fontReady && fontPath != nullptr) fontReady = tvg::Text::load(fontPath) == tvg::Result::Success;
    return true;
}

}  // namespace

void panelSetFont(const uint8_t* bytes, size_t size) {
    fontBytes = bytes;
    fontSize = size;
}

bool panelPaint(PanelDocument* document, uint32_t* pixels, int width, int height, const char* fontPath, PanelFrame* frame) {
    if (document == nullptr || pixels == nullptr || width <= 0 || height <= 0) return false;
    auto started = std::chrono::steady_clock::now();
    if (!ensureEngine(fontPath)) return false;
    auto canvas = tvg::SwCanvas::gen();
    if (canvas->target(pixels, static_cast<uint32_t>(width), static_cast<uint32_t>(width), static_cast<uint32_t>(height), tvg::ColorSpace::ABGR8888S) != tvg::Result::Success) {
        return false;
    }
    uint32_t paintColor = 0xFF000000u;
    float textSize = 0;
    for (const PanelNode& root : document->roots) paintNode(canvas, document, &root, 0, 0, &paintColor, &textSize);
    bool drew = canvas->draw(true) == tvg::Result::Success && canvas->sync() == tvg::Result::Success;
    delete canvas;
    if (!drew) return false;
    auto elapsed = std::chrono::steady_clock::now() - started;
    if (frame != nullptr) {
        frame->microseconds = static_cast<int>(std::chrono::duration_cast<std::chrono::microseconds>(elapsed).count());
        frame->milliseconds = frame->microseconds / 1000;
        frame->dirty = document->dirty;
        frame->documentBytes = document->byteSize;
        if (frame->dirty.w <= 0 || frame->dirty.h <= 0) frame->dirty = PanelRect{0, 0, width, height};
    }
    document->dirty = PanelRect{};
    return true;
}
