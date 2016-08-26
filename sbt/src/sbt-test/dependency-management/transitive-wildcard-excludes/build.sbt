
resolvers += {
  val f = baseDirectory.value / "repository"
  "local-test-repo" at f.getCanonicalFile.toURI.toASCIIString
}
libraryDependencies += "exclude.wildcard.test" % "top" % "1.0.0"

val checkDependencies = taskKey[Unit]("Checks if wildcard excludes work correctly.")

checkDependencies := {
  val hasTopJar = (fullClasspath in Compile).value.exists { jar => jar.data.getName contains "top-1.0.0.jar"}
  val hasBottomJar = (fullClasspath in Compile).value.exists { jar => jar.data.getName contains "bottom-1.0.0.jar"}
  val hasMiddleJar = (fullClasspath in Compile).value.exists { jar => jar.data.getName contains "middle-1.0.0.jar"}
  val errorJarString = (fullClasspath in Compile).value.map(_.data.getName).mkString(" * ", "\n * ", "")
  assert(hasTopJar, s"Failed to include dependency with wildcard exclusion on classpath!\nFound:\n$errorJarString")
  assert(hasMiddleJar, s"Failed to include dependency with wildcard exclusion on classpath!\nFound:\n$errorJarString")
  assert(!hasBottomJar, s"Failed to exclude transitive excluded dependency on classpath!\nFound:\n$errorJarString")
  val modules = (for {
    c <- update.value.configurations
    m <- c.modules
    if !m.evicted
  } yield m.module).distinct
  val hasTopDep = modules exists { m =>
    (m.organization == "exclude.wildcard.test") && (m.name == "top")
  }
  val hasMiddleDep = modules exists { m =>
    (m.organization == "exclude.wildcard.test") && (m.name == "middle")
  }
  val hasBottomDep = modules exists { m =>
    (m.organization == "exclude.wildcard.test") && (m.name == "bottom")
  }

  val errModuleString = modules.mkString("\n * ", "\n * ", "")
  assert(hasTopDep, s"Failed to include dependency with wildcard exclusion!\nFound:\n$errModuleString")
  assert(hasMiddleDep, s"Failed to include dependency with wildcard exclusion!\nFound:\n$errModuleString")
  assert(!hasBottomDep, s"Failed to exclude transitive excluded dependency!\nFound:\n$errModuleString")

}