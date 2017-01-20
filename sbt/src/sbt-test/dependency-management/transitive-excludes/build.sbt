

resolvers += {
  val f = baseDirectory.value / "repository"
  "local-test-repo" at f.getCanonicalFile.toURI.toASCIIString
}
libraryDependencies += "exclude.test" % "app" % "1.0.0"

val checkDependencies = taskKey[Unit]("Checks that dependencies are correct.")

checkDependencies := {
  val hasBadJar = (fullClasspath in Compile).value.exists { jar => jar.data.getName contains "bottom-1.0.0.jar"}
  val errorJarString = (fullClasspath in Compile).value.map(_.data.getName).mkString(" * ", "\n * ", "")
  val hasBadMiddleJar = (fullClasspath in Compile).value.exists { jar => jar.data.getName contains "middle-1.0.0.jar"}
  assert(!hasBadMiddleJar, s"Failed to exclude excluded dependency on classpath!\nFound:\n$errorJarString")
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
  val hasBadMiddleDep =
    modules exists { m =>
      (m.organization == "exclude.test") && (m.name == "middle")
    }
  val errModuleString = modules.mkString("\n * ", "\n * ", "")
  assert(!hasBadMiddleDep, s"Failed to exclude transitive excluded dependency!\nFound:\n$errModuleString")
  assert(!hasBadDep, s"Failed to exclude transitive excluded dependency!\nFound:\n$errModuleString")

}