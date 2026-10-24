#!/usr/bin/env bash
# Starts a RapidSwing application, building it first if it has not been built yet.
#
#   ./run.sh demo [options]         the demo app, e.g. ./run.sh demo --app.theme=dark --app.locale=de
#   ./run.sh generator              the application generator wizard
#   ./run.sh generator <options>    the generator's command line (see ./run.sh generator --help)
#
# Java: $JAVA_HOME, else java on the PATH. Extra JVM options can be set in $JAVA_OPTS.
set -euo pipefail

cd "$(dirname "$0")"

app="${1:-}"
case "$app" in
    demo) jar_pattern="rapidswing-demo/target/rapidswing-demo-*-app.jar" ;;
    generator) jar_pattern="rapidswing-generator/target/rapidswing-generator-*-app.jar" ;;
    -h|--help|"") sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown application: $app (use demo or generator)" >&2; exit 2 ;;
esac
shift

find_jar() {
    ls -1 $jar_pattern 2>/dev/null | head -n 1
}

jar="$(find_jar)"
if [ -z "$jar" ]; then
    echo "The $app has not been built yet; building it now (without tests)."
    ./build.sh --skip-tests
    jar="$(find_jar)"
fi

java_cmd="java"
if [ -n "${JAVA_HOME:-}" ]; then
    java_cmd="$JAVA_HOME/bin/java"
fi

# shellcheck disable=SC2086
exec "$java_cmd" ${JAVA_OPTS:-} -jar "$jar" "$@"
