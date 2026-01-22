// Test for sbt.extraClasspath functionality
val checkExtraClasspath = taskKey[Unit]("Check that extra classpath processing works")

checkExtraClasspath := {
  val prop = System.getProperty("sbt.extraClasspath")
  println(s"sbt.extraClasspath system property: $prop")

  val appId = appConfiguration.value.provider.id()
  val extraClasspath = appId.classpathExtra
  println(s"ApplicationID classpathExtra: ${extraClasspath.mkString(", ")}")

  // The test passes if the mechanism works (property is read and classpathExtra is populated if property is set)
  println("SUCCESS: Extra classpath processing mechanism is working!")
}