

resolvers += {
  val f = baseDirectory.value / "repository"
  "local-test-repo" at f.getCanonicalFile.toURI.toASCIIString
}
libraryDependencies += "exclude.test" % "app" % "1.0.0"

val checkDependencies = taskKey[Unit]("Checks that dependcies are correct.")

checkDependencies := {
  val hasBadJar = (fullClasspath in Compile).value.exists { jar => jar.data.getName contains "bottom-1.0.0.jar"}
  val errorJarString = (fullClasspath in Compile).value.map(_.data.getName).mkString(" * ", "\n * ", "")
  assert(!hasBadJar, s"Failed to exclude transitive excluded dependency on classpath!\nFound:\n$errorJarString")
  val modules =
    (for {
      c <- update.value.configurations
      m <- c.modules
      if !m.evicted
    } yield m.module).distinct
  val hasBadDep =
    modules exists { m =>
      (m.organization == "exclude.test") && (m.name == "bottom")
    }
  val errModuleString = modules.mkString("\n * ", "\n * ", "")
  assert(!hasBadDep, s"Failed to exclude transitive excluded dependency!\nFound:\n$errModuleString")

}