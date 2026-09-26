import java.util.concurrent.atomic.AtomicInteger

class Uncacheable:
  override def toString: String = "Uncacheable"

object Uncacheable:
  val counter = new AtomicInteger(0)
