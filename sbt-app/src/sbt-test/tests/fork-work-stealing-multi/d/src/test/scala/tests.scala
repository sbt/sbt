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
}

class dTest { @Test def t(): Unit = Rec.record("dTest") }
