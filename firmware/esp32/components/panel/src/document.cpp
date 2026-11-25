#include "panel_player.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <sstream>

namespace {

constexpr int OP_HEADER = 0;
constexpr int OP_WIDTH = 16;
constexpr int OP_PAINT = 40;
constexpr int OP_ROUNDED_CLIP = 54;
constexpr int OP_BACKGROUND = 55;
constexpr int OP_PADDING = 58;
constexpr int OP_CLICK = 59;
constexpr int OP_HEIGHT = 67;
constexpr int OP_TEXT = 102;
constexpr int OP_PATH = 123;
constexpr int OP_DRAW_PATH = 124;
constexpr int OP_MATRIX_SAVE = 130;
constexpr int OP_MATRIX_RESTORE = 131;
constexpr int OP_DRAW_TEXT = 133;
constexpr int OP_NAMED = 137;
constexpr int OP_COLOR = 138;
constexpr int OP_INT = 140;
constexpr int OP_FIT_BOX = 176;
constexpr int OP_ROOT = 200;
constexpr int OP_CONTENT = 201;
constexpr int OP_BOX = 202;
constexpr int OP_ROW = 203;
constexpr int OP_COLUMN = 204;
constexpr int OP_CANVAS = 205;
constexpr int OP_VISIBILITY = 211;
constexpr int OP_CONTAINER_END = 214;
constexpr int OP_HOST_META = 216;
constexpr int OP_STATE = 217;
constexpr int OP_COLLAPSIBLE = 233;
constexpr int OP_CORE_TEXT = 239;
constexpr int OP_CONSTRAINTS = 243;
constexpr int OP_SEMANTICS = 250;

constexpr int TYPE_EXACT = 0;
constexpr int TYPE_FILL = 1;
constexpr int TYPE_WRAP = 2;
constexpr int TYPE_WEIGHT = 3;
constexpr int TYPE_EXACT_DP = 6;
constexpr int TYPE_FILL_MAX_WIDTH = 7;
constexpr int TYPE_FILL_MAX_HEIGHT = 8;
constexpr int POS_START = 1;
constexpr int POS_CENTER = 2;
constexpr int POS_END = 3;
constexpr int POS_TOP = 4;
constexpr int POS_BOTTOM = 5;
constexpr int POS_SPACE_BETWEEN = 6;
constexpr int POS_SPACE_EVENLY = 7;
constexpr int POS_SPACE_AROUND = 8;

constexpr int VISIBLE = 1;
constexpr int OVERRIDE_VISIBLE = 32;

constexpr int DOC_WIDTH = 5;
constexpr int DOC_HEIGHT = 6;
constexpr int DOC_DENSITY = 7;
constexpr int HEADER_MAGIC = 0x048C0000;
constexpr int COLOR_REF = 2;

constexpr int PARAM_FLOAT = 2;
constexpr int PARAM_BOOL = 5;
constexpr int PARAM_INTS = 6;
constexpr int PARAM_FLOATS = 7;
constexpr int P_COLOR = 3;
constexpr int P_COLOR_ID = 4;
constexpr int P_FONT_SIZE = 5;
constexpr int P_FONT_WEIGHT = 7;
constexpr int P_TEXT_ALIGN = 9;
constexpr int P_LETTER_SPACING = 12;
constexpr int P_LINE_HEIGHT_ADD = 13;
constexpr int P_LINE_HEIGHT_MULTIPLIER = 14;
constexpr int P_UNDERLINE = 18;
constexpr int P_STRIKETHROUGH = 19;
constexpr int P_FONT_AXIS = 20;
constexpr int P_FONT_AXIS_VALUES = 21;
constexpr int P_AUTOSIZE = 22;
constexpr int P_MIN_FONT_SIZE = 25;
constexpr int P_MAX_FONT_SIZE = 26;

constexpr int CHANNEL_MAX = 255;
constexpr float TEXT_WIDTH_FACTOR = 0.55f;
constexpr float TEXT_LINE_FACTOR = 1.2f;

bool isContainer(int op) {
    switch (op) {
        case OP_CLICK:
        case OP_FIT_BOX:
        case OP_ROOT:
        case OP_CONTENT:
        case OP_BOX:
        case OP_ROW:
        case OP_COLUMN:
        case OP_CANVAS:
        case OP_STATE:
        case OP_COLLAPSIBLE:
        case OP_CORE_TEXT:
            return true;
        default:
            return false;
    }
}

bool isLayout(int op) {
    switch (op) {
        case OP_FIT_BOX:
        case OP_ROOT:
        case OP_BOX:
        case OP_ROW:
        case OP_COLUMN:
        case OP_CANVAS:
        case OP_STATE:
        case OP_COLLAPSIBLE:
        case OP_CORE_TEXT:
            return true;
        default:
            return false;
    }
}

bool isNan(float value) {
    uint32_t bits = 0;
    std::memcpy(&bits, &value, sizeof(bits));
    return (bits & 0x7f800000u) == 0x7f800000u && (bits & 0x007fffffu) != 0;
}

int idFromNan(float value) {
    uint32_t bits = 0;
    std::memcpy(&bits, &value, sizeof(bits));
    return static_cast<int>(bits & 0x3fffffu);
}

class Reader {
public:
    Reader(const uint8_t* bytes, size_t size) : bytes_(bytes), size_(size) {}

    bool ok() const { return error_.empty(); }
    const std::string& error() const { return error_; }
    size_t offset() const { return offset_; }
    bool remaining() const { return offset_ < size_; }

    int u8() {
        if (offset_ >= size_) {
            fail("truncated byte");
            return 0;
        }
        return bytes_[offset_++];
    }

    int u16() {
        int high = u8();
        int low = u8();
        return (high << 8) | low;
    }

    int i32() {
        uint32_t value = 0;
        for (int shift = 24; shift >= 0; shift -= 8) value |= static_cast<uint32_t>(u8()) << shift;
        return static_cast<int>(value);
    }

    float f32() {
        uint32_t bits = static_cast<uint32_t>(i32());
        float value = 0;
        std::memcpy(&value, &bits, sizeof(value));
        return value;
    }

    std::string utf8() {
        int count = i32();
        if (count < 0 || offset_ + static_cast<size_t>(count) > size_) {
            fail("bad string");
            return {};
        }
        std::string text(reinterpret_cast<const char*>(bytes_ + offset_), static_cast<size_t>(count));
        offset_ += static_cast<size_t>(count);
        return text;
    }

    void fail(const std::string& message) {
        if (!error_.empty()) return;
        std::ostringstream out;
        out << message << " at " << offset_;
        error_ = out.str();
    }

private:
    const uint8_t* bytes_;
    size_t size_;
    size_t offset_ = 0;
    std::string error_;
};

int parameterKind(int id) {
    switch (id) {
        case P_FONT_SIZE:
        case P_FONT_WEIGHT:
        case P_LETTER_SPACING:
        case P_LINE_HEIGHT_ADD:
        case P_LINE_HEIGHT_MULTIPLIER:
        case P_MIN_FONT_SIZE:
        case P_MAX_FONT_SIZE:
            return PARAM_FLOAT;
        case P_UNDERLINE:
        case P_STRIKETHROUGH:
        case P_AUTOSIZE:
            return PARAM_BOOL;
        case P_FONT_AXIS:
            return PARAM_INTS;
        case P_FONT_AXIS_VALUES:
            return PARAM_FLOATS;
        default:
            return 1;
    }
}

void readParameters(Reader* reader, int count, PanelNode* node) {
    for (int index = 0; index < count && reader->ok(); index++) {
        int id = reader->u8();
        switch (parameterKind(id)) {
            case PARAM_FLOAT: {
                float value = reader->f32();
                if (id == P_FONT_SIZE && !isNan(value)) node->fontSize = value;
                break;
            }
            case PARAM_BOOL:
                reader->u8();
                break;
            case PARAM_INTS: {
                int values = reader->u16();
                for (int value = 0; value < values; value++) reader->i32();
                break;
            }
            case PARAM_FLOATS: {
                int values = reader->u16();
                for (int value = 0; value < values; value++) reader->f32();
                break;
            }
            default: {
                int value = reader->i32();
                if (id == P_COLOR) node->textColor = static_cast<uint32_t>(value);
                if (id == P_COLOR_ID) node->textColorId = value;
                if (id == P_TEXT_ALIGN) node->align = value;
                break;
            }
        }
    }
}

void skipGradient(const std::vector<int>& words, size_t* index, int command) {
    if (*index >= words.size()) return;
    int colors = words[(*index)++] & 0xFF;
    *index += static_cast<size_t>(std::max(0, colors));
    if (*index >= words.size()) return;
    int stops = words[(*index)++];
    *index += static_cast<size_t>(std::max(0, stops));
    int kind = command >> 16;
    int extra = 0;
    if (kind == 0) extra = 5;
    else if (kind == 1) extra = 4;
    else if (kind == 2) extra = 2;
    else if (kind == 3) extra = 7;
    *index += static_cast<size_t>(extra);
}

void readPaint(Reader* reader, PanelNode* node) {
    int count = reader->i32();
    if (count < 0 || count > 4096) {
        reader->fail("bad paint");
        return;
    }
    node->fontSize = 0;
    std::vector<int> words(static_cast<size_t>(count));
    for (int index = 0; index < count; index++) words[static_cast<size_t>(index)] = reader->i32();
    size_t cursor = 0;
    while (cursor < words.size()) {
        int command = words[cursor++];
        int key = command & 0xFFFF;
        auto take = [&]() -> int {
            if (cursor >= words.size()) return 0;
            return words[cursor++];
        };
        switch (key) {
            case 1: {
                int bits = take();
                float size = 0;
                std::memcpy(&size, &bits, sizeof(size));
                if (size > 0.f) node->fontSize = size;
                break;
            }
            case 5:
            case 6:
            case 12:
            case 16:
            case 22:
            case 26:
                take();
                break;
            case 4:
                node->paintColor = static_cast<uint32_t>(take());
                node->hasPaint = true;
                node->paintIsRef = false;
                break;
            case 19:
                node->paintColorId = take();
                node->hasPaint = true;
                node->paintIsRef = true;
                break;
            case 9:
            case 13:
            case 20:
                take();
                break;
            case 23:
                cursor += static_cast<size_t>(std::max(0, command >> 16)) * 2;
                break;
            case 24:
                cursor += 3;
                break;
            case 25:
                cursor += static_cast<size_t>(std::max(0, command >> 16));
                break;
            case 11:
                skipGradient(words, &cursor, command);
                break;
            default:
                break;
        }
    }
}

void readHeader(Reader* reader, PanelDocument* document) {
    int major = reader->i32();
    reader->i32();
    reader->i32();
    if (major < 0x10000) {
        document->width = reader->i32();
        document->height = reader->i32();
        reader->i32();
        reader->i32();
        return;
    }
    if ((major & 0xFFFF0000) != HEADER_MAGIC) {
        reader->fail("bad header");
        return;
    }
    int length = reader->i32();
    if (length < 0 || length > 256) {
        reader->fail("bad header map");
        return;
    }
    for (int index = 0; index < length; index++) {
        int tag = reader->u16();
        reader->u16();
        int dataType = tag >> 10;
        int key = tag & 0x3F;
        if (dataType == 0) {
            int value = reader->i32();
            if (key == DOC_WIDTH) document->width = value;
            if (key == DOC_HEIGHT) document->height = value;
        } else if (dataType == 1) {
            float value = reader->f32();
            if (key == DOC_DENSITY && value > 0) document->density = value;
        } else if (dataType == 2) {
            reader->i32();
            reader->i32();
        } else if (dataType == 3) {
            reader->utf8();
        } else {
            reader->fail("bad header type");
            return;
        }
    }
}

bool readNode(Reader* reader, int op, PanelNode* node, PanelDocument* document);

bool readChildren(Reader* reader, PanelNode* parent, PanelDocument* document) {
    while (reader->ok() && reader->remaining()) {
        int op = reader->u8();
        if (op == OP_CONTAINER_END) return true;
        PanelNode child;
        if (!readNode(reader, op, &child, document)) return false;
        parent->children.push_back(std::move(child));
    }
    reader->fail("unclosed container");
    return false;
}

bool readNode(Reader* reader, int op, PanelNode* node, PanelDocument* document) {
    node->op = op;
    switch (op) {
        case OP_HEADER:
            readHeader(reader, document);
            break;
        case OP_WIDTH:
            node->widthType = reader->i32();
            node->widthValue = reader->f32();
            break;
        case OP_HEIGHT:
            node->heightType = reader->i32();
            node->heightValue = reader->f32();
            break;
        case OP_PADDING:
            node->padLeft = reader->f32();
            node->padTop = reader->f32();
            node->padRight = reader->f32();
            node->padBottom = reader->f32();
            break;
        case OP_BACKGROUND: {
            int flags = reader->i32();
            node->backgroundColorId = reader->i32();
            reader->i32();
            reader->i32();
            float red = reader->f32();
            float green = reader->f32();
            float blue = reader->f32();
            float alpha = reader->f32();
            reader->i32();
            node->hasBackground = true;
            node->backgroundIsRef = (flags & COLOR_REF) != 0;
            if (!node->backgroundIsRef) {
                auto channel = [](float value) {
                    float scaled = value <= 1.f ? value * CHANNEL_MAX : value;
                    return static_cast<uint32_t>(std::max(0, std::min(CHANNEL_MAX, static_cast<int>(std::lround(scaled)))));
                };
                node->backgroundArgb = (channel(alpha) << 24) | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
            }
            break;
        }
        case OP_ROUNDED_CLIP: {
            float corners[4] = {reader->f32(), reader->f32(), reader->f32(), reader->f32()};
            node->radius = 0;
            for (float corner : corners) node->radius = std::max(node->radius, isNan(corner) ? 0 : corner);
            break;
        }
        case OP_VISIBILITY:
            node->hasVisibility = true;
            node->visibilityId = reader->i32();
            break;
        case OP_TEXT: {
            int id = reader->i32();
            document->texts[id] = reader->utf8();
            node->textId = id;
            node->id = id;
            break;
        }
        case OP_NAMED: {
            int id = reader->i32();
            int type = reader->i32();
            std::string name = reader->utf8();
            document->names[name] = id;
            document->nameTypes[id] = type;
            node->id = id;
            break;
        }
        case OP_COLOR: {
            int id = reader->i32();
            document->colors[id] = static_cast<uint32_t>(reader->i32());
            node->id = id;
            break;
        }
        case OP_INT: {
            int id = reader->i32();
            document->ints[id] = reader->i32();
            node->id = id;
            break;
        }
        case OP_PATH: {
            int packed = reader->i32();
            int id = packed & 0xFFFFFF;
            int count = reader->i32();
            if (count < 0 || count > 100000) {
                reader->fail("bad path");
                break;
            }
            std::vector<float> points(static_cast<size_t>(count));
            for (int index = 0; index < count; index++) points[static_cast<size_t>(index)] = reader->f32();
            document->paths[id] = std::move(points);
            node->pathId = id;
            break;
        }
        case OP_DRAW_PATH:
            node->pathId = reader->i32();
            break;
        case OP_DRAW_TEXT:
            node->textId = reader->i32();
            node->anchorX = reader->f32();
            node->anchorY = reader->f32();
            node->panX = reader->f32();
            node->panY = reader->f32();
            reader->i32();
            break;
        case OP_PAINT:
            readPaint(reader, node);
            break;
        case OP_HOST_META: {
            reader->i32();
            int metadataId = reader->i32();
            auto found = document->texts.find(metadataId);
            if (found != document->texts.end()) node->action = found->second;
            node->textId = metadataId;
            break;
        }
        case OP_CONSTRAINTS:
            reader->u8();
            reader->f32();
            reader->f32();
            break;
        case OP_SEMANTICS:
            reader->i32();
            reader->u8();
            reader->i32();
            reader->i32();
            reader->u8();
            reader->u8();
            reader->u8();
            break;
        case OP_MATRIX_SAVE:
        case OP_MATRIX_RESTORE:
        case OP_CLICK:
            break;
        case OP_ROOT:
        case OP_CONTENT:
            node->id = reader->i32();
            break;
        case OP_CANVAS:
            node->id = reader->i32();
            reader->i32();
            break;
        case OP_BOX:
        case OP_FIT_BOX:
            node->id = reader->i32();
            reader->i32();
            node->alignH = reader->i32();
            node->alignV = reader->i32();
            break;
        case OP_ROW:
        case OP_COLUMN:
        case OP_COLLAPSIBLE:
            node->id = reader->i32();
            reader->i32();
            node->alignH = reader->i32();
            node->alignV = reader->i32();
            node->spacedBy = reader->f32();
            break;
        case OP_STATE:
            node->id = reader->i32();
            reader->i32();
            reader->i32();
            reader->i32();
            node->indexId = reader->i32();
            break;
        case OP_CORE_TEXT: {
            node->textId = reader->i32();
            int count = reader->u16();
            readParameters(reader, count, node);
            break;
        }
        default:
            reader->fail("opcode " + std::to_string(op));
            return false;
    }
    if (!reader->ok()) return false;
    if (isContainer(op)) return readChildren(reader, node, document);
    return true;
}

float literal(const PanelDocument* document, float value) {
    if (!isNan(value)) return value;
    auto found = document->floats.find(idFromNan(value));
    return found == document->floats.end() ? 0 : found->second;
}

bool fills(int type) {
    return type < 0 || type == TYPE_FILL || type == TYPE_WEIGHT || type == TYPE_FILL_MAX_WIDTH || type == TYPE_FILL_MAX_HEIGHT;
}

void absorb(PanelNode* node) {
    for (PanelNode& child : node->children) {
        if (child.op == OP_WIDTH) {
            node->widthType = child.widthType;
            node->widthValue = child.widthValue;
        } else if (child.op == OP_HEIGHT) {
            node->heightType = child.heightType;
            node->heightValue = child.heightValue;
        } else if (child.op == OP_PADDING) {
            node->padLeft = child.padLeft;
            node->padTop = child.padTop;
            node->padRight = child.padRight;
            node->padBottom = child.padBottom;
        } else if (child.op == OP_BACKGROUND) {
            node->hasBackground = true;
            node->backgroundIsRef = child.backgroundIsRef;
            node->backgroundColorId = child.backgroundColorId;
            node->backgroundArgb = child.backgroundArgb;
        } else if (child.op == OP_ROUNDED_CLIP) {
            node->radius = std::max(node->radius, child.radius);
        } else if (child.op == OP_VISIBILITY) {
            node->hasVisibility = true;
            node->visibilityId = child.visibilityId;
        } else if (child.op != OP_WIDTH) {
            absorb(&child);
        }
    }
}

void bindActions(PanelDocument* document, PanelNode* node) {
    if (node->op == OP_HOST_META && node->action.empty()) {
        auto found = document->texts.find(node->textId);
        if (found != document->texts.end()) node->action = found->second;
    }
    for (PanelNode& child : node->children) bindActions(document, &child);
}

void pullAction(PanelNode* node) {
    for (PanelNode& child : node->children) {
        pullAction(&child);
        bool click = child.op == OP_CLICK || child.op == OP_HOST_META;
        if (node->action.empty() && click) node->action = child.action;
    }
}

void resolveShow(PanelDocument* document, PanelNode* node) {
    if (node->hasVisibility) {
        int value = VISIBLE;
        auto found = document->ints.find(node->visibilityId);
        if (found != document->ints.end()) value = found->second;
        node->shown = value == VISIBLE || value == OVERRIDE_VISIBLE;
        node->takesSpace = node->shown || value == 2 || value == 64;
    }
    for (PanelNode& child : node->children) resolveShow(document, &child);
}

std::vector<PanelNode*> layoutKids(PanelNode* node) {
    std::vector<PanelNode*> kids;
    for (PanelNode& child : node->children) {
        if (child.op == OP_CONTENT) {
            for (PanelNode& nested : child.children) {
                if (isLayout(nested.op)) kids.push_back(&nested);
            }
            return kids;
        }
    }
    for (PanelNode& child : node->children) {
        if (isLayout(child.op)) kids.push_back(&child);
    }
    return kids;
}

float textWidth(const PanelDocument* document, const PanelNode* node) {
    const std::string* text = document->text(node->textId);
    float letters = text == nullptr ? 1.f : static_cast<float>(std::max<size_t>(1, text->size()));
    return letters * node->fontSize * TEXT_WIDTH_FACTOR;
}

struct Frame {
    float x;
    float y;
    float w;
    float h;
};

void place(PanelDocument* document, PanelNode* node, Frame area);

float along(const PanelDocument* document, PanelNode* node, float available, bool horizontal) {
    int type = horizontal ? node->widthType : node->heightType;
    float value = literal(document, horizontal ? node->widthValue : node->heightValue);
    if (type == TYPE_EXACT) return value;
    if (type == TYPE_EXACT_DP) return value * document->density;
    if (type == TYPE_WRAP) {
        if (node->op == OP_CORE_TEXT) return horizontal ? textWidth(document, node) : node->fontSize * TEXT_LINE_FACTOR;
        return available;
    }
    return available;
}

void placeKids(PanelDocument* document, PanelNode* node, Frame inner) {
    std::vector<PanelNode*> kids = layoutKids(node);
    if (node->op == OP_STATE) {
        int index = 0;
        auto found = document->ints.find(node->indexId);
        if (found != document->ints.end()) index = found->second;
        int seen = 0;
        for (PanelNode* kid : kids) {
            kid->shown = seen == index;
            kid->takesSpace = seen == index;
            if (seen == index) place(document, kid, inner);
            seen++;
        }
        return;
    }
    if (node->op == OP_FIT_BOX) {
        PanelNode* best = nullptr;
        for (PanelNode* kid : kids) {
            if (!kid->takesSpace) continue;
            float width = along(document, kid, inner.w, true);
            float height = along(document, kid, inner.h, false);
            bool fits = width <= inner.w + 1.f && height <= inner.h + 1.f;
            if (best == nullptr || (fits && kid->fontSize >= best->fontSize)) best = kid;
            kid->shown = false;
        }
        if (best != nullptr) {
            best->shown = true;
            place(document, best, inner);
        }
        return;
    }
    bool row = node->op == OP_ROW;
    bool column = node->op == OP_COLUMN || node->op == OP_COLLAPSIBLE;
    if (!row && !column) {
        for (PanelNode* kid : kids) {
            if (!kid->takesSpace) continue;
            float width = fills(kid->widthType) ? inner.w : along(document, kid, inner.w, true);
            float height = fills(kid->heightType) ? inner.h : along(document, kid, inner.h, false);
            float x = inner.x;
            float y = inner.y;
            if (node->alignH == POS_END) x += inner.w - width;
            else if (node->alignH == POS_CENTER) x += (inner.w - width) / 2.f;
            else if (node->alignH != POS_START) x = inner.x;
            if (node->alignV == POS_BOTTOM) y += inner.h - height;
            else if (node->alignV == POS_CENTER) y += (inner.h - height) / 2.f;
            else if (node->alignV != POS_TOP) y = inner.y;
            place(document, kid, Frame{x, y, std::max(0.f, width), std::max(0.f, height)});
        }
        return;
    }
    float gap = std::max(0.f, node->spacedBy);
    int flex = 0;
    int count = 0;
    float used = 0;
    for (PanelNode* kid : kids) {
        if (!kid->takesSpace) continue;
        count++;
        bool stretch = row ? fills(kid->widthType) : fills(kid->heightType);
        if (stretch) flex++;
        else used += row ? along(document, kid, inner.w, true) : along(document, kid, inner.h, false);
    }
    if (count > 1) used += gap * static_cast<float>(count - 1);
    float free = std::max(0.f, (row ? inner.w : inner.h) - used);
    float share = flex > 0 ? free / static_cast<float>(flex) : 0;
    int mainAlign = row ? node->alignH : node->alignV;
    float lead = 0;
    float between = 0;
    if (flex == 0 && count > 0) {
        if (mainAlign == POS_CENTER) lead = free / 2.f;
        else if (mainAlign == POS_END || mainAlign == POS_BOTTOM) lead = free;
        else if (mainAlign == POS_SPACE_EVENLY) {
            between = free / static_cast<float>(count + 1);
            lead = between;
        } else if (mainAlign == POS_SPACE_AROUND) {
            between = free / static_cast<float>(count);
            lead = between / 2.f;
        } else if (mainAlign == POS_SPACE_BETWEEN && count > 1) {
            between = free / static_cast<float>(count - 1);
        }
    }
    float cursor = (row ? inner.x : inner.y) + lead;
    int crossAlign = row ? node->alignV : node->alignH;
    for (PanelNode* kid : kids) {
        if (!kid->takesSpace) continue;
        bool stretch = row ? fills(kid->widthType) : fills(kid->heightType);
        float main = stretch ? share : (row ? along(document, kid, inner.w, true) : along(document, kid, inner.h, false));
        float crossAvail = row ? inner.h : inner.w;
        bool crossFill = row ? fills(kid->heightType) : fills(kid->widthType);
        float crossSize = crossFill ? crossAvail : (row ? along(document, kid, inner.h, false) : along(document, kid, inner.w, true));
        float cross = row ? inner.y : inner.x;
        if (crossAlign == POS_CENTER) cross += (crossAvail - crossSize) / 2.f;
        else if (crossAlign == POS_END || crossAlign == POS_BOTTOM) cross += crossAvail - crossSize;
        Frame child = row
            ? Frame{cursor, cross, main, std::max(0.f, crossSize)}
            : Frame{cross, cursor, std::max(0.f, crossSize), main};
        place(document, kid, child);
        cursor += main + gap + between;
    }
}

void place(PanelDocument* document, PanelNode* node, Frame area) {
    if (!node->takesSpace) {
        node->shown = false;
        return;
    }
    float padLeft = literal(document, node->padLeft);
    float padTop = literal(document, node->padTop);
    float padRight = literal(document, node->padRight);
    float padBottom = literal(document, node->padBottom);
    node->x = area.x;
    node->y = area.y;
    node->w = std::max(0.f, area.w);
    node->h = std::max(0.f, area.h);
    Frame inner{
        area.x + padLeft,
        area.y + padTop,
        std::max(0.f, area.w - padLeft - padRight),
        std::max(0.f, area.h - padTop - padBottom),
    };
    placeKids(document, node, inner);
}

void mark(PanelNode* node, int id, PanelRect* rect) {
    bool uses = node->textId == id || node->textColorId == id || node->backgroundColorId == id || node->visibilityId == id || node->indexId == id;
    if (uses && node->w > 0 && node->h > 0) {
        int left = static_cast<int>(std::floor(node->x));
        int top = static_cast<int>(std::floor(node->y));
        int right = static_cast<int>(std::ceil(node->x + node->w));
        int bottom = static_cast<int>(std::ceil(node->y + node->h));
        if (rect->w <= 0) {
            rect->x = left;
            rect->y = top;
            rect->w = right - left;
            rect->h = bottom - top;
        } else {
            int farX = std::max(rect->x + rect->w, right);
            int farY = std::max(rect->y + rect->h, bottom);
            rect->x = std::min(rect->x, left);
            rect->y = std::min(rect->y, top);
            rect->w = farX - rect->x;
            rect->h = farY - rect->y;
        }
    }
    for (PanelNode& child : node->children) mark(&child, id, rect);
}

std::string hitAt(const PanelNode* node, float x, float y, float* bestArea) {
    std::string found;
    if (node->shown && !node->action.empty() && x >= node->x && y >= node->y && x < node->x + node->w && y < node->y + node->h) {
        float area = node->w * node->h;
        if (area > 0 && area < *bestArea) {
            *bestArea = area;
            found = node->action;
        }
    }
    for (const PanelNode& child : node->children) {
        std::string nested = hitAt(&child, x, y, bestArea);
        if (!nested.empty()) found = nested;
    }
    return found;
}

}  // namespace

bool PanelDocument::load(const uint8_t* bytes, size_t size, std::string* error) {
    *this = PanelDocument{};
    byteSize = static_cast<int>(size);
    Reader reader(bytes, size);
    while (reader.ok() && reader.remaining()) {
        int op = reader.u8();
        PanelNode node;
        if (!readNode(&reader, op, &node, this)) break;
        roots.push_back(std::move(node));
    }
    if (!reader.ok()) {
        if (error != nullptr) *error = reader.error();
        return false;
    }
    for (PanelNode& root : roots) {
        absorb(&root);
        bindActions(this, &root);
        pullAction(&root);
    }
    if (width <= 0) width = 320;
    if (height <= 0) height = 240;
    layout();
    return true;
}

const std::string* PanelDocument::text(int id) const {
    auto found = texts.find(id);
    return found == texts.end() ? nullptr : &found->second;
}

uint32_t PanelDocument::colorOf(int id, uint32_t fallback) const {
    auto found = colors.find(id);
    return found == colors.end() ? fallback : found->second;
}

bool PanelDocument::setVar(const std::string& name, const std::string& type, const std::string& value) {
    std::string key = name;
    auto found = names.find(key);
    if (found == names.end() && key.rfind("USER:", 0) != 0) found = names.find("USER:" + key);
    if (found == names.end() && key.rfind("USER:", 0) == 0) found = names.find(key.substr(5));
    if (found == names.end()) return false;
    int id = found->second;
    bool changed = false;
    if (type == "s" || type == "string") {
        changed = texts[id] != value;
        texts[id] = value;
    } else if (type == "i" || type == "int") {
        int parsed = std::atoi(value.c_str());
        changed = ints[id] != parsed;
        ints[id] = parsed;
    } else if (type == "f" || type == "float") {
        float parsed = std::strtof(value.c_str(), nullptr);
        changed = floats[id] != parsed;
        floats[id] = parsed;
    } else if (type == "c" || type == "color") {
        uint32_t parsed = static_cast<uint32_t>(std::strtoul(value.c_str(), nullptr, 16));
        changed = colors[id] != parsed;
        colors[id] = parsed;
    }
    if (!changed) return false;
    PanelRect next;
        for (PanelNode& root : roots) mark(&root, id, &next);
    if (next.w > 0) dirty = next;
    return true;
}

void PanelDocument::layout() {
    for (PanelNode& root : roots) resolveShow(this, &root);
    Frame screen{0, 0, static_cast<float>(width), static_cast<float>(height)};
    for (PanelNode& root : roots) {
        if (root.op == OP_ROOT || isLayout(root.op)) place(this, &root, screen);
    }
    if (dirty.w <= 0) dirty = PanelRect{0, 0, width, height};
}

std::string PanelDocument::hit(float x, float y) const {
    float best = 1.0e12f;
    std::string action;
    for (const PanelNode& root : roots) {
        std::string found = hitAt(&root, x, y, &best);
        if (!found.empty()) action = found;
    }
    return action;
}
