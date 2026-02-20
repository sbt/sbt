semanticdbEnabled := true

val matrix = projectMatrix
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(scalaVersions =
    Seq(
      "2.12.21",
      "3.6.4"
    )
  )
