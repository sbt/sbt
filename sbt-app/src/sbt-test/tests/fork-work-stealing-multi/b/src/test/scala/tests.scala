import java.io.File
import org.junit.Test

/** One class only, so this project cannot spread and must occupy exactly one JVM. */
object Rec {
  private val dir = new File(System.getProperty("pids.dir"))
  private val proj = System.getProperty("proj")

  private def pid: String = {
    val vm = java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
    vm.takeWhile(_ != '@')
  }

  def record(suite: String): Unit = {
    dir.mkdirs()
    new File(dir, s"$proj.$suite.$pid").createNewFile()
    ()
  }

  /**
   * Waits until project a has started testing, which biases the run towards the interleaved case
   * where a and b test at once.
   *
   * It does not *guarantee* the interleaving: the marker stays on disk after a's JVM exits, so a
   * run that gave a the whole pool first would satisfy it from history. What proves the late
   * forking is the JVM count in build.sbt.
   *
   * Only b waits, not c and d: two waiters could occupy every slot on a machine with fewer
   * processors than the pool size, leaving nothing for a to be admitted into.
   */
  def awaitPeer(): Unit = {
    val deadline = System.currentTimeMillis() + 120000L
    def aRunning: Boolean =
      Option(dir.listFiles()).getOrElse(Array.empty[File]).exists(_.getName.startsWith("a."))
    while (!aRunning && System.currentTimeMillis() < deadline) Thread.sleep(50)
    if (!aRunning) throw new AssertionError("project a never started, so the pool was never shared")
  }
}

class bTest {
  @Test def t(): Unit = {
    Rec.record("bTest")
    Rec.awaitPeer()
  }
}
