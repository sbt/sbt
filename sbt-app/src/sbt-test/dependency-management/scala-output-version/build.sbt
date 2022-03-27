// cases 1, 2, 3: check for scala version in bar
// case a: check locally published Ivy dependency
// case b: check locally published Maven dependency
// case c: check unpublished sibling module dependency

val org = "org.example"
val fooName = "sbt-test-scala-output-version-foo"
val revision = "0.0.1-SNAPSHOT"

ThisBuild / organization := org
ThisBuild / version := revision

lazy val foo = project.in(file("foo"))
  .settings(
    name := fooName,
    scalaVersion := "3.1.2-RC2",
    crossScalaVersions := List("2.13.8", "3.1.2-RC2"),
    scalaOutputVersion := "3.0.2",
    scalaOutputVersion := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) => "3.0.2"
        case _ => scalaVersion.value
      }
    },
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.29" % Test
    ),
    TaskKey[Unit]("checkIvy") := {
      val ivyFile = makeIvyXml.value 
      val ivyContent = IO.read(ivyFile)
      val expectedContent = """<dependency org="org.scala-lang" name="scala3-library_3" rev="3.0.2" conf="compile->default(compile)">"""
      val hasCorrectStdlib = ivyContent.contains(expectedContent)
      if (!hasCorrectStdlib) sys.error(s"The produced Ivy file is incorrect:\n\n${ivyContent}")
    },
    TaskKey[Unit]("checkPom") := {
      val pomFile = makePom.value 
      val pomContent = IO.read(pomFile)
      val flatPom = pomContent.filterNot(_.isWhitespace)
      val expectedContent = "<groupId>org.scala-lang</groupId><artifactId>scala3-library_3</artifactId><version>3.0.2</version>"
      val hasCorrectStdlib = flatPom.contains(expectedContent)
      if (!hasCorrectStdlib) sys.error(s"The produced POM file is incorrect:\n\n${pomContent}")
    }
  )

val scala3_1 = Seq(scalaVersion := "3.1.1")
val scala3_0 = Seq(scalaVersion := "3.0.2")
val scala2_13 = Seq(scalaVersion := "2.13.8")
val ivyFooDep = Seq(
  libraryDependencies ++= Seq(
    org %% fooName % revision
  ),
  resolvers := Seq(Resolver.defaultLocal)
)
val mavenFooDep = Seq(
  libraryDependencies ++= Seq(
    org %% fooName % revision
  ),
  resolvers := Seq(Resolver.mavenLocal)
)

lazy val bar1a = project.in(file("bar1a"))
  .settings(
    scala3_1,
    ivyFooDep
  )

lazy val bar1b = project.in(file("bar1b"))
  .settings(
    scala3_1,
    mavenFooDep
  )

lazy val bar1c = project.in(file("bar1c"))
  .settings(
    scala3_1,
  ).dependsOn(foo)


lazy val bar2a = project.in(file("bar2a"))
  .settings(
    scala3_0,
    ivyFooDep
  )

lazy val bar2b = project.in(file("bar2b"))
  .settings(
    scala3_0,
    mavenFooDep
  )

lazy val bar2c = project.in(file("bar2c"))
  .settings(
    scala3_0,
  ).dependsOn(foo)


lazy val bar3a = project.in(file("bar3a"))
  .settings(
    scala2_13,
    ivyFooDep
  )

lazy val bar3b = project.in(file("bar3b"))
  .settings(
    scala2_13,
    mavenFooDep
  )

lazy val bar3c = project.in(file("bar3c"))
  .settings(
    scala2_13,
  ).dependsOn(foo)
