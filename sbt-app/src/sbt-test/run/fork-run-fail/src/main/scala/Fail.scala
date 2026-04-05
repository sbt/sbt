object Fail:
  def main(args: Array[String]): Unit =
    throw new RuntimeException("boom")
