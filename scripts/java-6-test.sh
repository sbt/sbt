#!/bin/bash
set -ev

# We're not using a jdk6 matrix entry with Travis here as some sources of coursier require Java 7 to compile
# (even though it won't try to call Java 7 specific methods if it detects it runs under Java 6).
# The tests here check that coursier is nonetheless fine when run under Java 6.

if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.11"; then
  ~/sbt ++${TRAVIS_SCALA_VERSION} cli/pack
  docker run -it --rm \
    -v $(pwd)/cli/target/pack:/opt/coursier \
    -e CI=true \
    openjdk:6-jre \
      /opt/coursier/bin/coursier fetch org.scalacheck::scalacheck:1.13.4

  docker run -it --rm \
    -v $(pwd)/cli/target/pack:/opt/coursier \
    -e CI=true \
    openjdk:6-jre \
      /opt/coursier/bin/coursier launch --help
fi

function clean_plugin_sbt() {
  mv plugins.sbt plugins.sbt0
  grep -v coursier plugins.sbt0 > plugins.sbt || true
  echo '
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-SNAPSHOT")
  ' >> plugins.sbt
}

if echo "$TRAVIS_SCALA_VERSION" | grep -q "^2\.10"; then
  ~/sbt ++${TRAVIS_SCALA_VERSION} publishLocal
  git clone https://github.com/alexarchambault/scalacheck-shapeless.git
  cd scalacheck-shapeless
  cd project
  clean_plugin_sbt
  cd project
  clean_plugin_sbt
  cd ../..
  docker run -it --rm \
    -v $HOME/.ivy2/local:/root/.ivy2/local \
    -v $HOME/sbt:/root/sbt \
    -v $(pwd):/root/project \
    -e CI=true \
    openjdk:6-jre \
      /bin/bash -c "cd /root/project && /root/sbt update"
  cd ..
fi
