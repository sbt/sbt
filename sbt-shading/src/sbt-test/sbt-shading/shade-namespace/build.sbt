
enablePlugins(coursier.ShadingPlugin)
shadingNamespace := "test.shaded"

shadeNamespaces += "argonaut"

libraryDependencies ++= Seq(
  "io.argonaut" %% "argonaut" % "6.2-RC2" % "shaded",
  // directly depending on that one for it not to be shaded
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

scalaVersion := "2.11.8"
organization := "io.get-coursier.test"
name := "shading-base-test"
version := "0.1.0-SNAPSHOT"

lazy val checkToShadeClasses = TaskKey[Unit]("check-to-shade-classes")

checkToShadeClasses := {
  val toShadeClasses0 = toShadeClasses.in(Shading).value

  if (toShadeClasses0.nonEmpty) {
    val log = streams.value.log
    log.error(s"Found ${toShadeClasses0.length} classes to be explicitly shaded")
    for (name <- toShadeClasses0)
      log.error("  " + name)
  }

  assert(toShadeClasses0.isEmpty)
}
