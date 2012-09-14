[sbt-launch]: http://repo.typesafe.com/typesafe/ivy-snapshots/org.scala-sbt/sbt-launch/

# Nightly Builds

Nightly builds are currently being published to <http://repo.typesafe.com/typesafe/ivy-snapshots/>.

To use a nightly build, follow the instructions for normal [[Setup|Getting Started Setup]], except:

1. Download the launcher jar from one of the subdirectories of [sbt-launch].  They should be listed in chronological order, so the most recent one will be last.
2. Call your script something like `sbt-nightly` to retain access to a stable `sbt` launcher.
3. The version number is the name of the subdirectory and is of the form `0.13.x-yyyyMMdd-HHmmss`.  Use this in a `build.properties` file.

Related to the third point, remember that an `sbt.version` setting in `<build-base>/project/build.properties` determines the version of sbt to use in a project.  If it is not present, the default version associated with the launcher is used.  This means that you must set `sbt.version=yyyyMMdd-HHmmss` in an existing `<build-base>/project/build.properties`.  You can verify the right version of sbt is being used to build a project by running `sbt-version`.

To reduce problems, it is recommended to not use a launcher jar for one nightly version to launch a different nightly version of sbt.
