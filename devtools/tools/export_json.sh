#!/bin/zsh
# Usage: export_json.sh <project.json> <out.mp4> on a debug test device.
exec python3 "${0:A:h}/export_fixture.py" video "$@"
