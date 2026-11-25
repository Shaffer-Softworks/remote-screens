#include "panel_player.h"

#include <fstream>
#include <iostream>
#include <string>
#include <vector>

namespace {

int inkCount(const std::vector<uint32_t>& pixels) {
    int ink = 0;
    for (uint32_t pixel : pixels) {
        if ((pixel & 0x00FFFFFFu) != 0) ink++;
    }
    return ink;
}

void dump(const PanelNode& node, int indent) {
    if (node.w > 0 || !node.action.empty() || node.op == 124 || node.op == 239) {
        std::cout << std::string(static_cast<size_t>(indent), ' ')
                  << node.op << " @" << static_cast<int>(node.x) << "," << static_cast<int>(node.y)
                  << " " << static_cast<int>(node.w) << "x" << static_cast<int>(node.h);
        if (!node.action.empty()) std::cout << " [" << node.action << "]";
        std::cout << "\n";
    }
    for (const PanelNode& child : node.children) dump(child, indent + 2);
}

bool writePpm(const std::string& path, const std::vector<uint32_t>& pixels, int width, int height) {
    std::ofstream out(path, std::ios::binary);
    if (!out) return false;
    out << "P6\n" << width << " " << height << "\n255\n";
    for (uint32_t pixel : pixels) {
        char channels[3] = {
            static_cast<char>(pixel & 0xFF),
            static_cast<char>((pixel >> 8) & 0xFF),
            static_cast<char>((pixel >> 16) & 0xFF),
        };
        out.write(channels, 3);
    }
    return static_cast<bool>(out);
}

std::string hitSomewhere(const PanelDocument& document) {
    for (int y = 0; y < document.height; y += 4) {
        for (int x = 0; x < document.width; x += 4) {
            std::string action = document.hit(static_cast<float>(x), static_cast<float>(y));
            if (action.find('|') != std::string::npos) return action;
        }
    }
    return {};
}

}  // namespace

int main() {
    std::string type;
    std::string pairingCode;
    std::string slot;
    int revision = 0;
    std::string encoded;
    std::vector<std::tuple<std::string, std::string, std::string>> vars;
    std::string welcome = "{\"type\":\"welcome\",\"pairingCode\":\"123456\",\"features\":[]}";
    if (!panelTakeMessage(welcome, &type, &pairingCode, &slot, &revision, &encoded, &vars) || pairingCode != "123456") {
        std::cerr << "welcome parse failed\n";
        return 1;
    }
    std::string hello = panelHello("panel-1", "Kitchen panel", 320, 240, 1.f);
    if (hello.find("\"capabilities\":[\"panel\"]") == std::string::npos || hello.find("token") != std::string::npos) {
        std::cerr << "hello is missing the panel capability\n";
        return 1;
    }
    std::string token;
    std::string adopted = "{\"type\":\"welcome\",\"token\":\"abc\",\"features\":[]}";
    if (!panelTakeMessage(adopted, &type, &pairingCode, &slot, &revision, &encoded, &vars, &token) || token != "abc") {
        std::cerr << "welcome token parse failed\n";
        return 1;
    }
    std::string trusted = panelHello("panel-1", "Kitchen panel", 320, 240, 1.f, "abc");
    if (trusted.find("\"token\":\"abc\"") == std::string::npos) {
        std::cerr << "hello omitted the token\n";
        return 1;
    }
    std::vector<uint32_t> badge(80 * 24, 0);
    panelDrawText(badge.data(), 80, 24, 80, "123456", 2, 2, 3, 0xFFFFFFFFu);
    if (inkCount(badge) < 20) {
        std::cerr << "pairing font drew nothing\n";
        return 1;
    }

    std::ifstream input(PANEL_RC_PATH, std::ios::binary);
    std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(input)), std::istreambuf_iterator<char>());
    if (bytes.empty()) {
        std::cerr << "missing " << PANEL_RC_PATH << "\n";
        return 1;
    }
    PanelDocument document;
    std::string error;
    if (!document.load(bytes.data(), bytes.size(), &error)) {
        std::cerr << "decode failed: " << error << "\n";
        return 1;
    }
    std::cout << "document bytes " << document.byteSize
              << " page " << document.width << "x" << document.height
              << " texts " << document.texts.size()
              << " paths " << document.paths.size()
              << " names " << document.names.size() << "\n";
    for (const PanelNode& root : document.roots) dump(root, 0);

    std::string action = hitSomewhere(document);
    if (action.empty()) {
        std::cerr << "no click action\n";
        return 1;
    }
    std::cout << "hit " << action << "\n";
    if (!document.hit(40.f, 40.f).empty()) {
        std::cerr << "clock area returned an action\n";
        return 1;
    }
    if (document.hit(150.f, 180.f).find('|') == std::string::npos) {
        std::cerr << "lamp tile did not return an action\n";
        return 1;
    }
    std::string wire = panelAction(7, action);
    if (wire.find(action) == std::string::npos) {
        std::cerr << "action was not encoded\n";
        return 1;
    }

    std::string renamed;
    for (const auto& named : document.names) {
        auto kind = document.nameTypes.find(named.second);
        if (kind != document.nameTypes.end() && kind->second == 0) {
            const std::string* before = document.text(named.second);
            std::string original = before == nullptr ? "" : *before;
            if (!document.setVar(named.first, "s", "12:34")) {
                std::cerr << "var " << named.first << " did not change\n";
                return 1;
            }
            const std::string* after = document.text(named.second);
            if (after == nullptr || *after != "12:34" || *after == original) {
                std::cerr << "var " << named.first << " was replaced in the document\n";
                return 1;
            }
            renamed = named.first;
            break;
        }
    }
    if (renamed.empty()) {
        std::cerr << "document has no named string\n";
        return 1;
    }
    document.layout();

    std::ifstream fontFile(PANEL_FONT_PATH, std::ios::binary);
    std::vector<uint8_t> fontBytes((std::istreambuf_iterator<char>(fontFile)), std::istreambuf_iterator<char>());
    if (!fontBytes.empty()) panelSetFont(fontBytes.data(), fontBytes.size());

    std::vector<uint32_t> pixels(static_cast<size_t>(document.width * document.height), 0);
    PanelFrame frame;
    if (!panelPaint(&document, pixels.data(), document.width, document.height, PANEL_FONT_PATH, &frame)) {
        std::cerr << "paint failed\n";
        return 1;
    }
    int ink = inkCount(pixels);
    std::cout << "frame us " << frame.microseconds
              << " dirty " << frame.dirty.x << "," << frame.dirty.y
              << " " << frame.dirty.w << "x" << frame.dirty.h
              << " ink " << ink << "\n";
    if (ink < 100) {
        std::cerr << "frame is blank\n";
        return 1;
    }
    if (frame.dirty.w <= 0 || frame.dirty.h <= 0 || frame.dirty.w > document.width || frame.dirty.h > document.height) {
        std::cerr << "dirty rectangle is outside the panel\n";
        return 1;
    }
    if (!writePpm(PANEL_PPM_PATH, pixels, document.width, document.height)) {
        std::cerr << "ppm was not written\n";
        return 1;
    }
    std::cout << "var " << renamed << " updated without a new document\n";
    return 0;
}
