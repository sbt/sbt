// Defines "macro" configuration.
// By default, this includes the dependencies of the normal sources.
// Drop the `extend(Compile)` to include no dependencies (not even scala-library) by default.
lazy val Macro = config("macro").hide.extend(Compile)

// Adds a "macro" configuration for macro dependencies.
ivyConfigurations += Macro

// add the compiler as a dependency for src/macro/
libraryDependencies +=
  scalaVersion("org.scala-lang" % "scala-compiler" % _ % Macro).value

// adds standard compile, console, package tasks for src/macro/
inConfig(Macro)(Defaults.configSettings)

// puts the compiled macro on the classpath for the main sources
unmanagedClasspath in Compile ++=
  (fullClasspath in Macro).value

// includes sources in src/macro/ in the main source package
mappings in (Compile, packageSrc) ++=
  (mappings in (Macro, packageSrc)).value

// Includes classes compiled from src/macro/ in the main binary
// This can be omitted if the classes in src/macro/ aren't used at runtime
mappings in (Compile, packageBin) ++=
  (mappings in (Macro, packageBin)).value
