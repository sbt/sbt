object Main {
  def main(args: Array[String]): Unit = {
    val version = coursier.util.Properties.version
    val expected = "2.0.0-RC6"
    assert(version == expected, s"version: $version, expected: $expected")
  }
}
