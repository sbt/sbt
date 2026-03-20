@transient
lazy val check = taskKey[Unit]("check")

scalaVersion := "3.8.3-RC1-bin-20260218-bb6fc60-NIGHTLY"
scalaOrganization := "ch.epfl.lara"
libraryDependencies += "com.lihaoyi" %% "fansi" % "0.5.0"
check := {
  val cp = (Compile / fullClasspath).value.map(_.data)
  cp.foreach: x =>
    assert(!x.toString.contains("org/scala-lang"), s"$x contains org/scala-lang")
}
