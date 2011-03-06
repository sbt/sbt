See <http://code.google.com/p/simple-build-tool/wiki/Setup> for setup instructions for the stable version of sbt.

To build from source, get the latest stable version of sbt (above) and get the code:

	$ git clone git://github.com/harrah/xsbt.git
	$ cd xsbt

The latest tag for 0.9.x is 0.9.1:

	$ git checkout v0.9.1

Or, get the development branch for 0.9.x:

	$ git checkout 0.9

To build:

	$ sbt update "project Launcher" proguard "project Simple Build Tool" "publish-local"

Copy your stable ~/bin/sbt script to ~/bin/xsbt and change it to use the launcher at:

	<xsbt>/target/sbt-launch-0.9.1.jar

If using the 0.9 development branch, the launcher is at:

	<xsbt>/target/sbt-launch-0.9.2-SNAPSHOT.jar