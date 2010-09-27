// The purpose of this test is to verify that sbt.TrapExit properly waits for Threads started after
//   the main method exits.
// The first thread waits 1s for the main method to exit and then creates another thread.
//   This thread waits another second before exiting.

object Spawn
{
	def main(args: Array[String])
	{
		(new ThreadA).start
	}
	class ThreadA extends Thread
	{
		override def run()
		{
			sleep()
			(new ThreadB).start()
		}
	}
	class ThreadB extends Thread
	{
		override def run() { sleep() }
	}
	private def sleep()
	{
		try { Thread.sleep(1000) }
		catch
		{
			case e: InterruptedException =>
				val msg = "TrapExit improperly interrupted non-daemon thread"
				System.err.println(msg)
				error(msg)
		}
	}
}