import Common._

name := "projectName"

k1 := {}

k2 := {
  println("This is k2")
}

val x = 5; k3 := {}; k4 := {}


lazy val root = (project in file(".")).
  settings(
    commands ++= Seq(UpdateK1, UpdateK3)
  )
