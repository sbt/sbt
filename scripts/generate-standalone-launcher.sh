#!/bin/bash
set -e

cd "$(dirname "$0")/.."

if [ ! -e cli/target/scala-2.11/proguard/coursier-standalone.jar ]; then
  echo "Generating proguarded JAR..." 1>&2
  sbt ++2.11.11 cli/proguard:proguard
fi

cat > coursier-standalone << EOF
#!/bin/sh
exec java -noverify -cp "\$0" coursier.cli.Coursier "\$@"
EOF
cat cli/target/scala-2.11/proguard/coursier-standalone.jar >> coursier-standalone
chmod +x coursier-standalone
