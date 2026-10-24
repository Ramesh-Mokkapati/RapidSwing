#!/usr/bin/env bash
# Compiles RapidSwing, runs the tests and packages the runnable jars of the demo and the generator.
#
#   ./build.sh                  full build with tests
#   ./build.sh --skip-tests     faster: compile and package only
#   ./build.sh --offline        use only the local Maven repository
#   ./build.sh -- <args>        pass further arguments to Maven
#
# Maven: $MAVEN_CMD, else mvn on the PATH, else a Maven wrapper download in ~/.m2/wrapper.
# Java:  $JAVA_HOME, else java on the PATH. Java 8 or newer.
set -euo pipefail

cd "$(dirname "$0")"

maven_args=(clean install)
while [ $# -gt 0 ]; do
    case "$1" in
        --skip-tests) maven_args+=(-DskipTests) ;;
        --offline) maven_args+=(-o) ;;
        --) shift; maven_args+=("$@"); break ;;
        -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
    esac
    shift
done

find_maven() {
    if [ -n "${MAVEN_CMD:-}" ]; then
        echo "$MAVEN_CMD"
    elif command -v mvn >/dev/null 2>&1; then
        command -v mvn
    else
        # The newest Maven unpacked by a Maven wrapper, if any.
        ls -d "$HOME"/.m2/wrapper/dists/apache-maven-*/*/apache-maven-*/bin/mvn 2>/dev/null | sort | tail -n 1
    fi
}

java_cmd="java"
if [ -n "${JAVA_HOME:-}" ]; then
    java_cmd="$JAVA_HOME/bin/java"
fi
if ! "$java_cmd" -version >/dev/null 2>&1; then
    echo "Java not found. Install Java 8 or newer, or set JAVA_HOME." >&2
    exit 1
fi

maven="$(find_maven)"
if [ -z "$maven" ]; then
    echo "Maven not found. Install Maven 3.6 or newer, put mvn on the PATH, or set MAVEN_CMD." >&2
    exit 1
fi

echo "Using $maven"
"$java_cmd" -version 2>&1 | head -n 1
"$maven" "${maven_args[@]}"

echo
echo "Built:"
ls -1 rapidswing-demo/target/*-app.jar rapidswing-generator/target/*-app.jar
echo "Start them with ./run.sh demo or ./run.sh generator"
