import java.io.File
import org.junit.Test

/**
 * Records which JVM ran which class, and holds every class until this project holds the whole pool.
 *
 * No timestamps: every assertion in build.sbt is about which class ran in which process.
 */
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

  private def myPids: Set[String] = {
    val files = Option(dir.listFiles()).getOrElse(Array.empty[File])
    files.map(_.getName).filter(_.startsWith(proj + ".")).map(_.split('.')(2)).toSet
  }

  /**
   * Blocks until this project holds the whole pool.
   *
   * Every class calls this, which is load-bearing: a worker runs one class at a time, so a class
   * waiting here pins its whole JVM, and if every class waits no worker can drain the queue before
   * the pool is full. Whichever worker is admitted into a slot b, c or d released therefore still
   * finds work.
   *
   * Having only the first class wait is not enough: a second worker admitted early would drain the
   * other nineteen while the first sits here, so every later worker finds the queue empty.
   *
   * The bound is a backstop, not a timing assertion: b, c and d have one trivial class each and
   * none of them waits on a's pool, so the slots a needs are always released.
   */
  def awaitPool(): Unit = {
    val want = System.getProperty("expect.jvms").toInt
    val deadline = System.currentTimeMillis() + 120000L
    while (myPids.size < want && System.currentTimeMillis() < deadline) Thread.sleep(50)
    if (myPids.size < want)
      throw new AssertionError(
        s"expected $want JVMs working on $proj, saw ${myPids.size}: ${myPids.toSeq.sorted}"
      )
  }

  def run(suite: String): Unit = {
    record(suite)
    awaitPool()
  }
}

class A00 { @Test def t(): Unit = Rec.run("A00") }
class A01 { @Test def t(): Unit = Rec.run("A01") }
class A02 { @Test def t(): Unit = Rec.run("A02") }
class A03 { @Test def t(): Unit = Rec.run("A03") }
class A04 { @Test def t(): Unit = Rec.run("A04") }
class A05 { @Test def t(): Unit = Rec.run("A05") }
class A06 { @Test def t(): Unit = Rec.run("A06") }
class A07 { @Test def t(): Unit = Rec.run("A07") }
class A08 { @Test def t(): Unit = Rec.run("A08") }
class A09 { @Test def t(): Unit = Rec.run("A09") }
class A10 { @Test def t(): Unit = Rec.run("A10") }
class A11 { @Test def t(): Unit = Rec.run("A11") }
class A12 { @Test def t(): Unit = Rec.run("A12") }
class A13 { @Test def t(): Unit = Rec.run("A13") }
class A14 { @Test def t(): Unit = Rec.run("A14") }
class A15 { @Test def t(): Unit = Rec.run("A15") }
class A16 { @Test def t(): Unit = Rec.run("A16") }
class A17 { @Test def t(): Unit = Rec.run("A17") }
class A18 { @Test def t(): Unit = Rec.run("A18") }
class A19 { @Test def t(): Unit = Rec.run("A19") }
