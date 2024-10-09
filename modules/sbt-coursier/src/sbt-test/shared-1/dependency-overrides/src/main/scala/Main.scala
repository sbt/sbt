import coursier.util.Properties

object Main extends App {
  val expected = "1.1.0-M14-7"
  assert(
    Properties.version == expected,
    s"Expected coursier-core $expected, got ${Properties.version}"
  )
}
