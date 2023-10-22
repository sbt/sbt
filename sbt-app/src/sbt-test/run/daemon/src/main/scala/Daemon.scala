object Daemon {
  def main(args: Array[String]): Unit = {
    val t = new Thread {
      override def run(): Unit = synchronized { wait() }
    }
    t.setDaemon(true);
    t.start
  }
}
