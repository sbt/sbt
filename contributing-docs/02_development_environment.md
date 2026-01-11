Development environment
=======================

Generally sbt can be developed on any environment that supports JDK and sbt.
This includes macOS, Linux, and modern Windows.

1. Install JDK (We recommend Azul Zulu JDK 8, 11, or 17 on macOS, and Temurin elsewhere).
2. Install `sbt`, following <https://www.scala-sbt.org/download>.

sbt acts as a package manager, so once you installed JDK and sbt, it will download necessary dependencies.

IDE support
-----------

Scala has two IDEs: IntelliJ (Scala plugin) and VS Code (Metals). Either should work.

### Note on supported JDK version for the sbt build

The sbt build itself currently doesn't support any JDK beyond version 25. You may run into deprecation warnings (which would become build errors due to build configuration) if you use any later JDK version to build sbt.

If you're using Metals as IDE, also check the `Java Version` setting. The default at the time of writing this is `17`, but this may change in the future, or you may have set it to a later version yourself. (Be aware that Metals may download a JDK in the background if you haven't switch to a local JDK matching the version before changing this setting. Also, this setting is currently only available as a `User` setting, so you can't set it for a single workspace. Don't forget to switch back if you need to for other projects).
