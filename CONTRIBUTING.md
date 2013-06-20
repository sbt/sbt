[Setup]: http://www.scala-sbt.org/release/docs/Getting-Started/Setup

# Issues and Pull Requests

## New issues

Please use the issue tracker to report confirmed bugs.
Do not use it to ask questions.
If you are uncertain whether something is a bug, please ask on the mailing list first.

When opening a new issue,

 * Please state the problem clearly and provide enough context. Code examples and build transcripts are often useful when appropriately edited.
 * Include all relevant information needed to reproduce such as the version of sbt and Scala being used.

Finally, thank you for taking the time to report a problem.

## Pull Requests

Whether implementing a new feature, fixing a bug, or modifying documentation, please work against the latest development branch (currently, 0.13).
Binary compatible changes will be backported to a previous series (currently, 0.12.x) at the time of the next stable release.
See below for instructions on building sbt from source.

## Documentation

Documentation fixes and contributions are welcome.
They are made via pull requests, as described in the previous section.
See below for details on getting sbt sources and modifying the documentation.

# Build from source

1. Install the current stable binary release of sbt (see [Setup]), which will be used to build sbt from source.
2. Get the source code.

		$ git clone git://github.com/sbt/sbt.git
		$ cd sbt

3. The initial branch is the development branch 0.13, which contains the latest code for the next major sbt release.  To build a specific release or commit, switch to the associated tag.  The tag for the latest stable release is v0.12.3:

		$ git checkout v0.12.3

	Note that sbt is always built with the previous stable release.  For example, the 0.13 branch is built with 0.12.4-RC1, the v0.11.2 tag is built with 0.11.1, and the v0.11.0 tag is built with 0.10.1.

4. To build the launcher and publish all components locally,

		$ sbt
		> publish-local

	To build documentation, run `make-site` or the individual commands directly:

		> doc
		> sphinx:mappings
		> sxr

   (Note: sxr currently doesn't work for macros, so it doesn't work for sbt right now.)

5. To use this locally built version of sbt, copy your stable `~/bin/sbt` script to `~/bin/xsbt` and change it to use the launcher jar in `<sbt>/target/`.  For the v0.12.3 tag, the full location is:

		<sbt>/target/sbt-launch-0.12.3.jar

	If using the 0.13 development branch, the launcher is at:

		<sbt>/target/sbt-launch-0.13.0-SNAPSHOT.jar

## Modifying sbt

1. When developing sbt itself, run `compile` when checking compilation only.

2. To use your modified version of sbt in a project locally, run `publish-local`.  If you have modified the launcher, also run `proguard`.

3. After each `publish-local`, clean the `~/.sbt/boot/` directory.  Alternatively, if sbt is running and the launcher hasn't changed, run `reboot full` to have sbt do this for you.

4. If a project has `project/build.properties` defined, either delete the file or change `sbt.version` to `0.13.0-SNAPSHOT`.

## Building Documentation

The scala-sbt.org site documentation is built using sphinx and requires some external packages to be manually installed first:

```text
$ pip install pygments
$ pip install sphinx
$ pip install sphinxcontrib-issuetracker
```

To build the full site, run the `make-site` task, which will generate the manual, API, SXR, and other site pages in `target/site/`.
To only work on the site and not API or SXR, run `sphinx:mappings`.
To only build API documentation, run `doc`.  Sphinx is not required for generating API or SXR documentation.