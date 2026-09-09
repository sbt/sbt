package example

import java.io.{ File, FileWriter }
import java.lang.management.ManagementFactory
import munit.FunSuite

class Marker extends FunSuite:
  test("mark") {
    val pid = ManagementFactory.getRuntimeMXBean.getName
    val w = new FileWriter(new File("pids"), true)
    try w.write(pid + "\n")
    finally w.close()
  }
