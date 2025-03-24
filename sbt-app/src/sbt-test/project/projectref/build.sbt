// https://github.com/sbt/sbt/issues/7738
val root = (project in file("."))
  .dependsOn(ProjectRef(file("a1"), "a1"))
