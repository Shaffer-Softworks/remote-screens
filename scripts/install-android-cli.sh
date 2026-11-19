#!/usr/bin/env bash
# Installs the Android CLI dev channel into ~/.local/bin and adds that directory to the shell profile.
# The published installer detects the OS and CPU. On Windows, use the .cmd installer in
# .cursor/skills/android-cli/SKILL.md.

set -euo pipefail

curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/install.sh | bash -s dev
