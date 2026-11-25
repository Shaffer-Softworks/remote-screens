#include "panel_player.h"

#include <cctype>
#include <cstdlib>
#include <sstream>

namespace {

constexpr int GLYPH_WIDTH = 5;
constexpr int GLYPH_HEIGHT = 7;
constexpr int CHANNEL_MASK = 0xFF;

const uint8_t* glyph(char letter) {
    static const uint8_t DIGITS[][GLYPH_HEIGHT] = {
        {0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E},
        {0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E},
        {0x0E, 0x11, 0x01, 0x02, 0x04, 0x08, 0x1F},
        {0x1E, 0x01, 0x01, 0x0E, 0x01, 0x01, 0x1E},
        {0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02},
        {0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E},
        {0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E},
        {0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08},
        {0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E},
        {0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C},
    };
    static const uint8_t LETTERS[][GLYPH_HEIGHT] = {
        {0x0E, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11},
        {0x1E, 0x11, 0x11, 0x1E, 0x11, 0x11, 0x1E},
        {0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E},
        {0x1E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x1E},
        {0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x1F},
        {0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x10},
        {0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0F},
        {0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11},
        {0x0E, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0E},
        {0x01, 0x01, 0x01, 0x01, 0x11, 0x11, 0x0E},
        {0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11},
        {0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F},
        {0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11},
        {0x11, 0x19, 0x15, 0x13, 0x11, 0x11, 0x11},
        {0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E},
        {0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10},
        {0x0E, 0x11, 0x11, 0x11, 0x15, 0x12, 0x0D},
        {0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11},
        {0x0F, 0x10, 0x10, 0x0E, 0x01, 0x01, 0x1E},
        {0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04},
        {0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E},
        {0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04},
        {0x11, 0x11, 0x11, 0x15, 0x15, 0x15, 0x0A},
        {0x11, 0x11, 0x0A, 0x04, 0x0A, 0x11, 0x11},
        {0x11, 0x11, 0x0A, 0x04, 0x04, 0x04, 0x04},
        {0x1F, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1F},
    };
    unsigned char value = static_cast<unsigned char>(letter);
    if (value >= 'a' && value <= 'z') value = static_cast<unsigned char>(value - 'a' + 'A');
    if (value >= '0' && value <= '9') return DIGITS[value - '0'];
    if (value >= 'A' && value <= 'Z') return LETTERS[value - 'A'];
    return nullptr;
}

std::string jsonEscape(const std::string& text) {
    std::string out;
    for (char letter : text) {
        if (letter == '"' || letter == '\\') out.push_back('\\');
        out.push_back(letter);
    }
    return out;
}

const char* findKey(const std::string& json, const char* key, size_t from = 0) {
    std::string pattern = std::string("\"") + key + "\"";
    size_t at = json.find(pattern, from);
    if (at == std::string::npos) return nullptr;
    at = json.find(':', at + pattern.size());
    if (at == std::string::npos) return nullptr;
    return json.c_str() + at + 1;
}

void skipSpace(const char** cursor) {
    while (**cursor != '\0' && std::isspace(static_cast<unsigned char>(**cursor))) (*cursor)++;
}

bool readJsonString(const char* cursor, std::string* out) {
    skipSpace(&cursor);
    if (*cursor != '"') return false;
    cursor++;
    out->clear();
    while (*cursor != '\0' && *cursor != '"') {
        if (*cursor == '\\' && cursor[1] != '\0') {
            cursor++;
            out->push_back(*cursor);
        } else {
            out->push_back(*cursor);
        }
        cursor++;
    }
    return *cursor == '"';
}

int base64Value(char letter) {
    if (letter >= 'A' && letter <= 'Z') return letter - 'A';
    if (letter >= 'a' && letter <= 'z') return letter - 'a' + 26;
    if (letter >= '0' && letter <= '9') return letter - '0' + 52;
    if (letter == '+') return 62;
    if (letter == '/') return 63;
    return -1;
}

}  // namespace

void panelDrawText(uint32_t* pixels, int width, int height, int stride, const char* text, int originX, int originY, int scale, uint32_t abgr) {
    if (pixels == nullptr || text == nullptr || scale < 1) return;
    int cursor = originX;
    for (const char* letter = text; *letter != '\0'; letter++) {
        const uint8_t* rows = glyph(*letter);
        if (rows != nullptr) {
            for (int row = 0; row < GLYPH_HEIGHT; row++) {
                for (int column = 0; column < GLYPH_WIDTH; column++) {
                    if ((rows[row] & (1 << (GLYPH_WIDTH - 1 - column))) == 0) continue;
                    for (int y = 0; y < scale; y++) {
                        int pixelY = originY + row * scale + y;
                        if (pixelY < 0 || pixelY >= height) continue;
                        for (int x = 0; x < scale; x++) {
                            int pixelX = cursor + column * scale + x;
                            if (pixelX < 0 || pixelX >= width) continue;
                            pixels[pixelY * stride + pixelX] = abgr;
                        }
                    }
                }
            }
        }
        cursor += (GLYPH_WIDTH + 1) * scale;
    }
}

std::string panelHello(
    const std::string& deviceId,
    const std::string& deviceName,
    int widthDp,
    int heightDp,
    float density,
    const std::string& token
) {
    std::ostringstream out;
    out << "{\"type\":\"hello\",\"protocol\":1,\"deviceId\":\"" << jsonEscape(deviceId)
        << "\",\"deviceName\":\"" << jsonEscape(deviceName) << "\"";
    if (!token.empty()) out << ",\"token\":\"" << jsonEscape(token) << "\"";
    out << ",\"widthDp\":" << widthDp
        << ",\"heightDp\":" << heightDp
        << ",\"density\":" << density
        << ",\"appVersion\":1,\"capabilities\":[\"panel\"]}";
    return out.str();
}

std::string panelAction(int revision, const std::string& metadata) {
    std::ostringstream out;
    out << "{\"type\":\"action\",\"slot\":\"main\",\"rev\":" << revision
        << ",\"metadata\":\"" << jsonEscape(metadata) << "\",\"page\":0}";
    return out.str();
}

bool panelDecodeBase64(const std::string& text, std::vector<uint8_t>* out) {
    out->clear();
    int accumulator = 0;
    int bits = 0;
    for (char letter : text) {
        if (std::isspace(static_cast<unsigned char>(letter)) || letter == '=') continue;
        int value = base64Value(letter);
        if (value < 0) return false;
        accumulator = (accumulator << 6) | value;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out->push_back(static_cast<uint8_t>((accumulator >> bits) & CHANNEL_MASK));
        }
    }
    return !out->empty();
}

bool panelTakeMessage(
    const std::string& json,
    std::string* type,
    std::string* pairingCode,
    std::string* slot,
    int* revision,
    std::string* documentBase64,
    std::vector<std::tuple<std::string, std::string, std::string>>* vars,
    std::string* token
) {
    const char* typeAt = findKey(json, "type");
    if (typeAt == nullptr || !readJsonString(typeAt, type)) return false;
    if (*type == "welcome") {
        if (pairingCode != nullptr) {
            const char* codeAt = findKey(json, "pairingCode");
            if (codeAt != nullptr) readJsonString(codeAt, pairingCode);
        }
        if (token != nullptr) {
            const char* tokenAt = findKey(json, "token");
            if (tokenAt != nullptr) readJsonString(tokenAt, token);
        }
    }
    if (*type == "document") {
        const char* slotAt = findKey(json, "slot");
        if (slotAt != nullptr && slot != nullptr) readJsonString(slotAt, slot);
        const char* revAt = findKey(json, "rev");
        if (revAt != nullptr && revision != nullptr) *revision = std::atoi(revAt);
        const char* dataAt = findKey(json, "data");
        if (dataAt != nullptr && documentBase64 != nullptr) readJsonString(dataAt, documentBase64);
    }
    if (*type == "vars" && vars != nullptr) {
        const char* list = findKey(json, "vars");
        if (list == nullptr) return true;
        const std::string body(list);
        size_t cursor = 0;
        while ((cursor = body.find("\"name\"", cursor)) != std::string::npos) {
            std::string name;
            std::string varType;
            std::string value;
            const char* nameAt = findKey(body, "name", cursor);
            const char* typeAtVar = findKey(body, "type", cursor);
            const char* valueAt = findKey(body, "value", cursor);
            if (nameAt == nullptr || !readJsonString(nameAt, &name)) break;
            if (typeAtVar != nullptr) readJsonString(typeAtVar, &varType);
            if (valueAt != nullptr) readJsonString(valueAt, &value);
            vars->emplace_back(name, varType, value);
            cursor += 6;
        }
    }
    return true;
}
