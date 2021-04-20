ThisBuild / scalaVersion := "3.0.0-M3"

lazy val check = taskKey[Unit]("check the Scala 3 instance class loader")

lazy val xsbtiClass = classOf[xsbti.compile.Compilers]

check := {
  val scala3Loader = scalaInstance.value.loader
  assert(
    scala3Loader.loadClass(xsbtiClass.getCanonicalName) == xsbtiClass,
    "The Scala 3 instance classloader does not load the same `xsbti` classes than sbt"
  )
}