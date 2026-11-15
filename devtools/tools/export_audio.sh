#!/bin/zsh
# Usage: export_audio.sh <project.json> <out.m4a|wav> on a debug test device.
exec python3 "${0:A:h}/export_fixture.py" audio "$@"
