[Google Code]: http://code.google.com/p/simple-build-tool
[Northeast Scala Symposium]: http://www.nescala.org/2011/
[documentation]: https://github.com/harrah/xsbt/wiki
[Setup]: https://github.com/harrah/xsbt/wiki/Setup
[video of a demo]: http://vimeo.com/20263617

# sbt 0.10

This is the 0.10.x series of sbt.  See [Setup] for getting started with the latest binary release or see below to build from source.

The previous stable release of sbt was 0.7.7, which was hosted on [Google Code].

There is a [video of a demo] given at the [Northeast Scala Symposium] that gives a brief introduction to the concepts in sbt 0.9 and later.  Note that the demo was based on 0.9.0 and things have changed since then.  See the [documentation] for current information.

# Build from source

To build from source, get the latest stable version of sbt 0.10.x (see [Setup]) and get the code.

	$ git clone git://github.com/harrah/xsbt.git
	$ cd xsbt

The initial branch is the development branch 0.10, which contains the latest code for the 0.10.x series.

The latest tag for 0.10.x is 0.10.1:

	$ git checkout v0.10.1

To build the launcher, publish all components locally, and build API and SXR documentation:

	$ sbt build-all

The individual commands are

   $ sbt publish-local proguard sxr doc

Copy your stable ~/bin/sbt script to ~/bin/xsbt and change it to use the launcher at:

	<xsbt>/target/sbt-launch-0.10.1.jar

If using the 0.10 development branch, the launcher is at:

	<xsbt>/target/sbt-launch-0.10.2-SNAPSHOT.jar

## Modifying sbt

When developing sbt itself, there is no need to run `build-all`, since this generates documentation as well.  For the fastest turnaround time for checking compilation only, run `compile`.

To use your modified version of sbt in a project locally, run `publish-local`.  If you have modified the launcher, also run `proguard`.

After each `publish-local`, clean the `project/boot/` directory in the project in which you want to use the locally built sbt.  Alternatively, if sbt is running and the launcher hasn't changed, run `reboot full` to have sbt do this for you.

If a project has `project/build.properties` defined, either delete the file or change `sbt.version` to `0.10.2-SNAPSHOT`.