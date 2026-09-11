lazy val checkSlf4jBinding = taskKey[Unit]("Checks that sbt provides an SLF4J binding")

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.36"

checkSlf4jBinding := {
  Class.forName("org.slf4j.impl.StaticLoggerBinder")
  val factory = org.slf4j.LoggerFactory.getILoggerFactory
  assert(factory.getClass.getName == "org.slf4j.helpers.NOPLoggerFactory", factory.getClass.getName)
}
