package sbt

final class Time(val startLabel: String)
{
	val start = System.nanoTime
	var last = start
}
object Time
{
	val start = System.nanoTime
	private[this] var stack: List[Time] = Nil
	
	def timeString(t: Long): String = {
		val ms = (t / 1e6).toInt
		val s = (ms / 1e3).toString
		val d = 5 - s.length
		if(d > 0) s + ("0"*d) else s
	}
	private[this] def print(pre: String, times: Seq[Long], label: String, diff: Long): Unit =
		println(times.map(t => timeString(t) + "\t ").mkString(pre, "", label + " (" + timeString(diff) + ")"))

	def block(startLabel: String) = synchronized {
		val t = new Time(startLabel)
		stack = t :: stack
		display("+ ", startLabel)
		t
	}
	def apply(label: String) = synchronized {
		display("  ", label)
	}
	private[this] def current = {
		if(stack.isEmpty) {
			System.out.println("ERROR: empty stack")
			stack = (new Time("ERROR")) :: Nil
		}
		stack.head
	}
	private[this] def display(pre: String, label: String) = synchronized {
		val now = System.nanoTime
		print(pre, stack.reverseMap(t => now - t.start), label, now - current.last)
		current.last = now
	}
	def complete(lastLabel: String) = synchronized {
		apply(lastLabel)
		val label = current.startLabel
		denest()
		display("- ", label)
	}
	
	private def denest(): Unit = synchronized {
		if(stack.isEmpty)
			error("Denesting empty stack!")
		else
			stack = stack.tail
	}
}
