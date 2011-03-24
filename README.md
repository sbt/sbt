This is the 0.9.x development series of sbt.  The current stable release of sbt is 0.7.4, which can be downloaded from the [http://code.google.com/p/simple-build-tool](Google Code project).

See <http://code.google.com/p/simple-build-tool/wiki/Setup> for setup instructions for the stable version of sbt.

To build from source, get the latest stable version of sbt (above) and get the code.

	$ git clone -n git://github.com/harrah/xsbt.git
	$ cd xsbt

The '-n' option is strictly only necessary when using msysgit on Windows.
(This works around an issue with spaces in the 'master' branch by not checking the 'master' branch out initially.)

The latest tag for 0.9.x is 0.9.2:

	$ git checkout v0.9.2

Or, get the development branch for 0.9.x:

	$ git checkout 0.9

To build:

	$ sbt update "project Launcher" proguard "project Simple Build Tool" "publish-local"

Copy your stable ~/bin/sbt script to ~/bin/xsbt and change it to use the launcher at:

	<xsbt>/target/sbt-launch-0.9.2.jar

If using the 0.9 development branch, the launcher is at:

	<xsbt>/target/sbt-launch-0.9.3-SNAPSHOT.jar

There is a [video of a demo](http://vimeo.com/20263617) given at the [Northeast Scala Symposium](http://www.nescala.org/2011/) that gives a brief introduction to ideas in sbt 0.9.  Note that the demo was based on 0.9.0 and some things have changed since then.  See the [documentation](https://github.com/harrah/xsbt/wiki) for current information.