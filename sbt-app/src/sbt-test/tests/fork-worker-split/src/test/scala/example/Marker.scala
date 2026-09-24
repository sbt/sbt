package example

import java.io.{ File, PrintWriter }
import java.lang.management.ManagementFactory
import java.nio.file.Files
import scala.util.control.NonFatal
import munit.FunSuite

trait Marker extends FunSuite:
  def n: Int
  test(s"mark $n") {
    val pid = ManagementFactory.getRuntimeMXBean.getName
    val w = new PrintWriter(new File(s"seen-$n"))
    try w.print(pid)
    finally w.close()

    // Concurrency probe: announce this JVM as running, wait long enough for siblings to do the
    // same, then snapshot how many *distinct JVMs* (not threads) are announced right now. Fingerprint
    // by pid, not just presence, since one JVM's own threads (testForkedParallelism) would otherwise
    // inflate the count without proving another JVM was involved.
    val running = new File(s"running-$n")
    Files.write(running.toPath, pid.getBytes)
    Thread.sleep(1000)
    val peers = new File(".").listFiles().toVector
      .filter(_.getName.startsWith("running-"))
      .flatMap:f =>
        try
          Some(new String(Files.readAllBytes(f.toPath)))
        catch
          case NonFatal(_) => None
      .toSet
    val pw = new PrintWriter(new File(s"peers-$n"))
    try pw.print(peers.size)
    finally pw.close()
    running.delete()
    ()
  }
end Marker

class Test1 extends Marker { def n = 1 }
class Test2 extends Marker { def n = 2 }
class Test3 extends Marker { def n = 3 }
class Test4 extends Marker { def n = 4 }
class Test5 extends Marker { def n = 5 }
class Test6 extends Marker { def n = 6 }
