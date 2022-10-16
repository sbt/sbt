package spec

import zio.test._

object Spec extends ZIOSpecDefault {
  def spec = suite("Spec")(
    test("test") {
      assertTrue(1 == 1)
    }
  )
}