#!/usr/bin/env bash
# Puts the demonstration website together in target/site: the pages, the screenshots and the demo jar.
# Run ./build.sh first. Preview it with: python3 -m http.server -d target/site 8080
set -euo pipefail

cd "$(dirname "$0")/.."
out=target/site
rm -rf "$out"
mkdir -p "$out/images"

cp site/index.html site/try.html site/style.css "$out/"
cp docs/images/*.png "$out/images/"
jar=$(ls -1 rapidswing-demo/target/rapidswing-demo-*-app.jar | head -n 1)
cp "$jar" "$out/rapidswing-demo.jar"
# Serve files as they are, without Jekyll processing.
touch "$out/.nojekyll"

echo "Site assembled in $out"
