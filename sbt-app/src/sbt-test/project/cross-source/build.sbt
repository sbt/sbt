val commonSettings = Seq(
  crossScalaVersions := (0 to 6).map(i => s"2.10.$i") ++ (0 to 11).map(i => s"2.11.$i") ++ (0 to 2).map(i => s"2.12.$i")
)

val p1 = project.in(file("p1")).settings(commonSettings)
val p2 = project.in(file("p2")).settings(commonSettings)
val p3 = project.in(file("p3")).settings(commonSettings)
val p4 = project.in(file("p4")).settings(commonSettings)
