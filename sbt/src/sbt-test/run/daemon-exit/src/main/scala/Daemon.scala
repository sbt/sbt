// This test verifies that System.exit from a daemon thread works properly

object DaemonExit
{
	def main(args: Array[String]): Unit = {
		val t = new Thread {
			override def run(): Unit = {
				Thread.sleep(1000)
				System.exit(0)
			}
		}
	//	t.setDaemon(true)
		t.start()

		val t2 = new Thread {
			override def run(): Unit = synchronized { wait() }
		}
		t2.start()
	}
}
