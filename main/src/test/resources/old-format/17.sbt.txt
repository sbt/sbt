name := "Programming Scala, Second Edition: Code examples"

version := "2.0"

organization := "org.programming-scala"

scalaVersion := "2.11.2"

// Build against several versions of Scala
crossScalaVersions := Seq("2.11.2", "2.10.4")

// Scala 2.11 split the standard library into smaller components.
// The XML and parser combinator support are now separate jars.
// I use the if condition to conditionally add these extra dependencies. 
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"  % "2.3.3",
  "org.scalaz"        %% "scalaz-core" % "7.0.6",
  "org.scalacheck"    %% "scalacheck"  % "1.11.4" % "test",
  "org.scalatest"     %% "scalatest"   % "2.2.0"  % "test",
  "org.specs2"        %% "specs2"      % "2.3.12" % "test",
  // JUnit is used for some Java interop. examples. A driver for JUnit:
  "junit"        % "junit-dep"       % "4.10" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test"
) ++ (
  if (scalaVersion.value startsWith "2.11") 
    Seq(
      // Could use this to get everything:
      // "org.scala-lang.modules" %% "scala-library-all" % "2.11.1")
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1",
      "org.scala-lang.modules" %% "scala-xml" % "1.0.2")
  else Seq.empty)

val commonOptions = Seq(
  "-encoding", "UTF-8", "-optimise", 
  "-deprecation", "-unchecked", "-feature", "-Xlint")
// "-explaintypes" - Use when you need more detailed messages for type errors.
// "-Yinline-warnings" - Warns if constructs have the @inline annotation, but
// inlining isn't possible. Can be more annoying than useful most of the time,
// but consider using it for performance critical code.

// Options passed to the Scala and Java compilers:
scalacOptions <<= scalaVersion map { version: String => 
  if (version.startsWith("2.10")) commonOptions
  else commonOptions ++ Seq("-Ywarn-infer-any") // Warn if "Any" is inferred
}

javacOptions  ++= Seq(
  "-Xlint:unchecked", "-Xlint:deprecation") // Java 8: "-Xdiags:verbose")

// Enable improved incremental compilation feature in 2.11.X.
// see http://www.scala-lang.org/news/2.11.1
incOptions := incOptions.value.withNameHashing(true)
