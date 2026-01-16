// https://github.com/sbt/sbt/issues/8458

val foo = projectMatrix
  .jvmPlatform(
    scalaVersions = Seq("2.13.18", "3.3.7"),
    crossVersion = CrossVersion.full
  )
