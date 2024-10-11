scalaVersion := "2.12.20"
crossScalaVersions := List("2.12.20", "2.13.12")

val setLastModified = taskKey[Unit]("Sets the last modified time for classfiles")
setLastModified := {
  val versions = crossScalaVersions.value
  versions.map(_.split('.').take(2).mkString("scala-", ".", "")).foreach { v =>
    val f = target.value / v / "classes" / "A.class"
    Stamps.value.put(f, IO.getModifiedTimeOrZero(f))
  }
}

val checkLastModified = taskKey[Unit]("Checks the last modified time for classfiles")
checkLastModified := {
  val versions = crossScalaVersions.value
  versions.map(_.split('.').take(2).mkString("scala-", ".", "")).foreach { v =>
    val classFile = target.value / v / "classes" / "A.class"
    val actual = IO.getModifiedTimeOrZero(classFile)
    val previous = Stamps.value.get(classFile)
    assert(actual == previous, s"$actual did not equal $previous for $classFile")
  }
}
