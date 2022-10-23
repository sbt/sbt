import sjsonnew.BasicJsonProtocol._

lazy val x = taskKey[Int]("x")
lazy val y = taskKey[Int]("y")
lazy val checkScopes = inputKey[Unit]("check scopes")

lazy val subA = project
lazy val subB = project

x := 3

Compile / y / x := 7

Runtime / y / x := 13

subA / Compile / x := {
  val xcy = (Compile / y / x).previous getOrElse 0  // 7
  // verify that This is properly resolved to Global and not the defining key's scope
  val xg = x.previous getOrElse 0  // 3
  println(s"xcy=$xcy, xg=$xg")
  xcy * xg
}

inConfig(Compile)(Seq(
  subB / y := {
    // verify that the referenced key gets delegated
    val xty = (Test / y / x).previous getOrElse 0  // 13
    // verify that inConfig gets applied
    val xcy = (y / x).previous getOrElse 0  // 7
    println(s"xcy=$xcy, xty=$xty")
    xty * xcy
  }
))

def parser = {
  import complete.DefaultParsers._
  (Space ~> IntBasic) ~ (Space ~> IntBasic)
}

checkScopes := {
  val (expectedX, expectedY) = parser.parsed
  val actualX = (subA/ Compile / x).value
  val actualY = (subB / Test / y).value
  assert(actualX == expectedX, s"Expected 'x' to be $expectedX, got $actualX")
  assert(actualY == expectedY, s"Expected 'y' to be $expectedY, got $actualY")
}
