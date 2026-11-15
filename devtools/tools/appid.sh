#!/bin/zsh
# Prints the app's applicationId as app/build.gradle.kts sets it (release round R26): every dev script reads it from here,
# so changing the package name for the store (A1) is one line in the build file. The qa build adds ".qa".
sed -n 's/^ *applicationId = "\(.*\)".*/\1/p' ${0:A:h:h:h}/app/build.gradle.kts | head -1
