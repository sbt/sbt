semanticdbEnabled := true

val matrix = projectMatrix
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(scalaVersions =
    Seq(
      "2.12.21", // semanticdb support via semanticdb-scalac
      "3.6.4" // built-in semanticdb support
    )
  )
