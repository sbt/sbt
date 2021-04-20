package testpkg

import munit._

class ClueSuite extends FunSuite {
  val x = 42
  val y = 32
  checkPrint(
    "clues",
    clues(x, y),
    """|Clues {
       |  x: Int = 42
       |  y: Int = 32
       |}
       |""".stripMargin
  )

  val z: List[Int] = List(1)
  checkPrint(
    "list",
    clues(z),
    """|Clues {
       |  z: List[Int] = List(
       |    1
       |  )
       |}
       |""".stripMargin
  )

  def checkPrint(
      options: TestOptions,
      clues: Clues,
      expected: String
  )(implicit loc: Location): Unit = {
    test(options) {
      val obtained = munitPrint(clues)
      assertNoDiff(obtained, expected)
    }
  }
}
