#!/bin/sh
#
# Gradle start-up script for POSIX shells.
#
set -e

APP_HOME=$(cd -P "$(dirname "$0")" > /dev/null && pwd -P) || exit
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=$(command -v java) || {
        echo "ERROR: JAVA_HOME is not set and no 'java' command could be found on your PATH." >&2
        echo "Please set JAVA_HOME to a JDK 17 installation, or install one." >&2
        exit 1
    }
fi

exec "$JAVACMD" \
    -Xmx64m -Xms64m \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
