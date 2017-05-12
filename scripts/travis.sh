#!/usr/bin/env bash
set -ev

SCALA_VERSION="${SCALA_VERSION:-${TRAVIS_SCALA_VERSION:-2.12.1}}"
PULL_REQUEST="${PULL_REQUEST:-${TRAVIS_PULL_REQUEST:-false}}"
BRANCH="${BRANCH:-${TRAVIS_BRANCH:-$(git rev-parse --abbrev-ref HEAD)}}"
PUBLISH="${PUBLISH:-0}"
SCALA_JS="${SCALA_JS:-0}"

JARJAR_VERSION="${JARJAR_VERSION:-1.0.1-coursier-SNAPSHOT}"

setupCoursierBinDir() {
  mkdir -p bin
  cp coursier bin/
  export PATH="$(pwd)/bin:$PATH"
}

downloadInstallSbtExtras() {
  curl -L -o bin/sbt https://github.com/paulp/sbt-extras/raw/9ade5fa54914ca8aded44105bf4b9a60966f3ccd/sbt
  chmod +x bin/sbt
}

launchTestRepo() {
  ./scripts/launch-test-repo.sh "$@"
}

integrationTestsRequirements() {
  # Required for ~/.ivy2/local repo tests
  sbt ++2.11.11 coreJVM/publishLocal http-server/publishLocal

  # Required for HTTP authentication tests
  launchTestRepo --port 8080 --list-pages

  # Required for missing directory listing tests (no --list-pages)
  launchTestRepo --port 8081
}

setupCustomJarjar() {
  if [ ! -d "$HOME/.m2/repository/org/anarres/jarjar/jarjar-core/$JARJAR_VERSION" ]; then
    git clone https://github.com/alexarchambault/jarjar.git
    cd jarjar
    if ! grep -q "^version=$JARJAR_VERSION\$" gradle.properties; then
      echo "Expected jarjar version not found" 1>&2
      exit 1
    fi
    git checkout 249c8dbb970f8
    ./gradlew :jarjar-core:install
    cd ..
    rm -rf jarjar
  fi
}

isScalaJs() {
  [ "$SCALA_JS" = 1 ]
}

sbtCoursier() {
  [ "$SBT_COURSIER" = 1 ]
}

sbtShading() {
  [ "$SBT_SHADING" = 1 ]
}

is210() {
  echo "$SCALA_VERSION" | grep -q "^2\.10"
}

is211() {
  echo "$SCALA_VERSION" | grep -q "^2\.11"
}

is212() {
  echo "$SCALA_VERSION" | grep -q "^2\.12"
}

runSbtCoursierTests() {
  sbt ++$SCALA_VERSION coreJVM/publishLocal cache/publishLocal extra/publishLocal "sbt-coursier/scripted sbt-coursier/*"
  if [ "$SCALA_VERSION" = "2.10" ]; then
    sbt ++$SCALA_VERSION "sbt-coursier/scripted sbt-coursier-0.13/*"
  fi
}

runSbtShadingTests() {
  sbt ++$SCALA_VERSION coreJVM/publishLocal cache/publishLocal extra/publishLocal sbt-coursier/publishLocal "sbt-shading/scripted sbt-shading/*"
  if [ "$SCALA_VERSION" = "2.10" ]; then
    sbt ++$SCALA_VERSION "sbt-shading/scripted sbt-shading-0.13/*"
  fi
}

jsCompile() {
  sbt ++$SCALA_VERSION js/compile js/test:compile coreJS/fastOptJS fetch-js/fastOptJS testsJS/test:fastOptJS js/test:fastOptJS
}

jvmCompile() {
  sbt ++$SCALA_VERSION jvm/compile jvm/test:compile
}

runJsTests() {
  sbt ++$SCALA_VERSION js/test
}

runJvmTests() {
  sbt ++$SCALA_VERSION jvm/test jvm/it:test
}

validateReadme() {
  sbt ++${SCALA_VERSION} tut
}

checkBinaryCompatibility() {
  sbt ++${SCALA_VERSION} coreJVM/mimaReportBinaryIssues cache/mimaReportBinaryIssues
}

testLauncherJava6() {
  sbt ++${SCALA_VERSION} cli/pack
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
}

testSbtCoursierJava6() {
  sbt ++${SCALA_VERSION} coreJVM/publishLocal cache/publishLocal extra/publishLocal sbt-coursier/publishLocal

  git clone https://github.com/alexarchambault/scalacheck-shapeless.git
  cd scalacheck-shapeless
  cd project
  clean_plugin_sbt
  cd project
  clean_plugin_sbt
  cd ../..
  docker run -it --rm \
    -v $HOME/.ivy2/local:/root/.ivy2/local \
    -v $(pwd):/root/project \
    -v $(pwd)/../bin:/root/bin \
    -e CI=true \
    openjdk:6-jre \
      /bin/bash -c "cd /root/project && /root/bin/sbt update"
  cd ..

  # ensuring resolution error doesn't throw NoSuchMethodError
  mkdir -p foo/project
  cd foo
  echo 'libraryDependencies += "foo" % "bar" % "1.0"' >> build.sbt
  echo 'addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-SNAPSHOT")' >> project/plugins.sbt
  echo 'sbt.version=0.13.15' >> project/build.properties
  docker run -it --rm \
    -v $HOME/.ivy2/local:/root/.ivy2/local \
    -v $(pwd):/root/project \
    -v $(pwd)/../bin:/root/bin \
    -e CI=true \
    openjdk:6-jre \
      /bin/bash -c "cd /root/project && /root/bin/sbt update || true" | tee -a output
  grep "coursier.ResolutionException: Encountered 1 error" output
  echo "Ok, found ResolutionException in output"
  cd ..
}

clean_plugin_sbt() {
  mv plugins.sbt plugins.sbt0
  grep -v coursier plugins.sbt0 > plugins.sbt || true
  echo '
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-SNAPSHOT")
  ' >> plugins.sbt
}

publish() {
  sbt ++${SCALA_VERSION} publish
}

testBootstrap() {
  if is211; then
    sbt ++${SCALA_VERSION} echo/publishLocal cli/pack
    cli/target/pack/bin/coursier bootstrap -o cs-echo io.get-coursier:echo_2.11:1.0.0-SNAPSHOT
    if [ "$(./cs-echo foo)" != foo ]; then
      echo "Error: unexpected output from bootstrapped echo command." 1>&2
      exit 1
    fi
  fi
}


# TODO Add coverage once https://github.com/scoverage/sbt-scoverage/issues/111 is fixed

setupCustomJarjar

setupCoursierBinDir
downloadInstallSbtExtras

if isScalaJs; then
  jsCompile
  runJsTests
else
  integrationTestsRequirements
  jvmCompile

  if sbtCoursier; then
    if is210 || is212; then
      runSbtCoursierTests
    fi

    if is210; then
      testSbtCoursierJava6
    fi
  elif sbtShading; then
    if is210 || is212; then
      runSbtShadingTests
    fi
  else
    runJvmTests

    testBootstrap

    validateReadme
    checkBinaryCompatibility

    if is211; then
      testLauncherJava6
    fi
  fi

  # Not using a jdk6 matrix entry with Travis as some sources of coursier require Java 7 to compile
  # (even though it won't try to call Java 7 specific methods if it detects it runs under Java 6).
  # The tests here check that coursier is nonetheless fine when run under Java 6.
fi


if [ "$PUBLISH" = 1 -a "$PULL_REQUEST" = false -a "$BRANCH" = master ]; then
  publish

  if is211 && isScalaJs; then
    #"$(dirname "$0")/push-gh-pages.sh" "$SCALA_VERSION"
    :
  fi
fi

