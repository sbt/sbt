import sbt.librarymanagement.DependencyMode.Direct

autoScalaLibrary := false
libraryDependencies ++= Seq(
  "junit" % "junit" % "4.13.2" % Test,
  "com.github.sbt" % "junit-interface" % "0.13.2" % Test,
)
dependencyMode := Direct

lazy val checkTestClasspath = taskKey[Unit]("check that Test classpath includes transitive deps")

checkTestClasspath := {
  val cp = (Test / dependencyClasspath).value.map(_.data.id)
  // junit's transitive dep hamcrest-core must be on the test runtime classpath
  assert(cp.exists(_.contains("hamcrest-core")),
    s"Expected hamcrest-core in Test/dependencyClasspath, got: $cp")
  assert(cp.exists(_.contains("junit")),
    s"Expected junit in Test/dependencyClasspath, got: $cp")
  assert(cp.exists(_.contains("junit-interface")),
    s"Expected junit-interface in Test/dependencyClasspath, got: $cp")
}
