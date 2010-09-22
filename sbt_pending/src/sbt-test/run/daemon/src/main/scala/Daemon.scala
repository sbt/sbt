object Daemon
{
	def main(args: Array[String])
	{
		val t = new Thread {
			override def run() {
				synchronized { wait() }
			}
		}
		t.setDaemon(true);
		t.start
	}
}