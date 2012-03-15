[Google Code]: http://code.google.com/p/simple-build-tool
[Northeast Scala Symposium]: http://www.nescala.org/2011/
[Scala Days 2011]: http://days2011.scala-lang.org/node/138/285
[documentation]: https://github.com/harrah/xsbt/wiki
[Setup]: https://github.com/harrah/xsbt/wiki/Getting-Started-Setup
[video of a demo]: http://vimeo.com/20263617
[FAQ]: https://github.com/harrah/xsbt/wiki/FAQ

# sbt 0.11

This is the 0.11.x series of sbt (soon to be 0.12.x).

 * [Setup]: Describes getting started with the latest binary release.  See below to build from source.
 * [FAQ]: Explains how to get help, how to report an issue, and how to contribute.
 * There is a [video of a demo] given at [Scala Days 2011] based on sbt 0.10.0 that gives an introduction to the configuration system in sbt 0.10.0 and later.  See the [documentation] for current information.
 * [Google Code]: hosts sbt 0.7.7 and earlier versions

# Build from source

1. Install the current stable binary release of sbt (see [Setup]), which will be used to build sbt from source.
2. Get the source code.

		$ git clone git://github.com/harrah/xsbt.git
		$ cd xsbt

3. The initial branch is the development branch 0.12, which contains the latest code for the 0.12.x series.  To build a specific release or commit, switch to the associated tag.  The tag for the latest stable release is v0.12.0-M2:

		$ git checkout v0.12.0-M2

	Note that sbt is always built with the previous stable release.  For example, the 0.12 branch is built with 0.12.0-M2, the v0.11.2 tag is built with 0.11.1, and the v0.11.0 tag is built with 0.10.1.

4. To build the launcher, publish all components locally, and build API and SXR documentation:

		$ sbt build-all

	Alternatively, the individual commands run by `build-all` may be executed directly:

		$ sbt publish-local proguard sxr doc

5. To use this locally built version of sbt, copy your stable ~/bin/sbt script to ~/bin/xsbt and change it to use the launcher jar in `<xsbt>/target/`.  For the v0.12.0-M2 tag, the full location is:

		<xsbt>/target/sbt-launch-0.12.0-M2.jar

	If using the 0.12 development branch, the launcher is at:

		<xsbt>/target/sbt-launch-0.12.0-SNAPSHOT.jar

## Modifying sbt

When developing sbt itself, there is no need to run `build-all`, since this generates documentation as well.  For the fastest turnaround time for checking compilation only, run `compile`.

To use your modified version of sbt in a project locally, run `publish-local`.  If you have modified the launcher, also run `proguard`.

After each `publish-local`, clean the `~/.sbt/boot/` directory.  Alternatively, if sbt is running and the launcher hasn't changed, run `reboot full` to have sbt do this for you.

If a project has `project/build.properties` defined, either delete the file or change `sbt.version` to `0.12.0-SNAPSHOT`.
