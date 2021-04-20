// The purpose of this test is to verify that sbt.TrapExit properly waits for Threads started after
//   the main method exits and that it handles System.exit from a second generation thread.
// The first thread waits 1s for the main method to exit and then creates another thread.
//   This thread waits another second before calling System.exit.  The first thread hangs around to
//   ensure that TrapExit actually processes the exit.

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
			Thread.sleep(1000)
			(new ThreadB).start()
			synchronized { wait() }
		}
	}
	class ThreadB extends Thread
	{
		override def run()
		{
			Thread.sleep(1000)
			System.exit(0)
		}
	}
}