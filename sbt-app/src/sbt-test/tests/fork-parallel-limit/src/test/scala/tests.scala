import java.io.File

/**
 * Proves that N forked test groups really overlap, without sampling.
 *
 * Each class marks its arrival and then blocks until it can see `expect.peak` arrivals. The marks
 * are append-only, so once the Nth arrives every one of those N is still sitting in the barrier —
 * which makes "N ran at the same time" a fact rather than an inference from timing. If fewer than N
 * groups may run at once, the first wave waits out the deadline and fails.
 */
object Barrier {
  private val arrivals = new File("arrivals")

  def arrive(name: String): Unit = {
    val want = System.getProperty("expect.peak").toInt
    arrivals.mkdirs()
    new File(arrivals, name).createNewFile()
    def seen: Int = Option(arrivals.list()).map(_.length).getOrElse(0)
    val deadline = System.currentTimeMillis() + 90000L
    while (seen < want && System.currentTimeMillis() < deadline) Thread.sleep(25)
    if (seen < want)
      throw new AssertionError(
        s"expected $want forked test groups to run at once, only ever saw $seen"
      )
  }
}

class G0 { @org.junit.Test def t(): Unit = Barrier.arrive("G0") }
class G1 { @org.junit.Test def t(): Unit = Barrier.arrive("G1") }
class G2 { @org.junit.Test def t(): Unit = Barrier.arrive("G2") }
class G3 { @org.junit.Test def t(): Unit = Barrier.arrive("G3") }
class G4 { @org.junit.Test def t(): Unit = Barrier.arrive("G4") }
class G5 { @org.junit.Test def t(): Unit = Barrier.arrive("G5") }
class G6 { @org.junit.Test def t(): Unit = Barrier.arrive("G6") }
class G7 { @org.junit.Test def t(): Unit = Barrier.arrive("G7") }
