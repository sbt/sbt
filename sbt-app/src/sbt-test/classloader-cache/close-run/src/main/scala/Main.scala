object Main {
  class Foo

  def main(args: Array[String]): Unit = {
    new Thread {
      override def run(): Unit = {
        Thread.sleep(500)
        try new Foo
        catch { case t: Throwable => sys.exit(1) }
      }
    }.start()
  }
}
