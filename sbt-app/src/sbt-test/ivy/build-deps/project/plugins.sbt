libraryDependencies += {
  val sbtV = sbtVersion.value
  ("org.scala-sbt" % s"sbt-ivy_sbt${sbtV}_${scalaBinaryVersion.value}" % sbtV).intransitive()
}
