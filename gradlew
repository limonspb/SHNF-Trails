#!/usr/bin/env sh
# Gradle wrapper script (Unix)
APP_HOME="$(dirname "$(readlink -f "$0")")"
exec java -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
