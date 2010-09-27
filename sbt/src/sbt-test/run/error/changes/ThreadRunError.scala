object Spawn
{
	def main(args: Array[String])
	{
		(new ThreadA).start
	}
	class ThreadA extends Thread
	{
		override def run(): Unit = error("Test error thread")
	}
}