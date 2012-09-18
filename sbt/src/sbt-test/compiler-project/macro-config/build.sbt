// Adds a "macro" configuration for macro dependencies.
// By default, this includes the dependencies of the normal sources.
// Drop the `extend(Compile)` to include no dependencies (not even scala-library) by default.
ivyConfigurations += config("macro").hide.extend(Compile)

// add the compiler as a dependency for src/macro/
libraryDependencies <+=
  scalaVersion("org.scala-lang" % "scala-compiler" % _ % "macro")

// adds standard compile, console, package tasks for src/macro/
inConfig(config("macro"))(Defaults.configSettings)

// puts the compiled macro on the classpath for the main sources
unmanagedClasspath in Compile <++=
  fullClasspath in config("macro")

// includes sources in src/macro/ in the main source package
mappings in (Compile, packageSrc) <++=
  mappings in (config("macro"), packageSrc)

// Includes classes compiled from src/macro/ in the main binary
// This can be omitted if the classes in src/macro/ aren't used at runtime
mappings in (Compile, packageBin) <++=
  mappings in (config("macro"), packageBin)

scalaVersion := "2.10.0-M7"