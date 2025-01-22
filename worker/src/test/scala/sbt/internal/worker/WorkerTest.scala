package sbt.internal.worker

import java.util.Properties
import sbt.io.IO

object WorkerTest extends verify.BasicTestSuite:
  val main = WorkerMain()

  test("run props") {
    val prop = Properties()
    prop.setProperty("mainClass", "example.Hello")
    prop.setProperty("classpath0000", IO.classLocationPath(classOf[example.Hello]).toString)
    prop.setProperty("classpath0001", IO.classLocationPath(classOf[scala.quoted.Quotes]).toString)
    prop.setProperty("classpath0002", IO.classLocationPath(classOf[scala.AnyVal]).toString)
    prop.setProperty("args0000", "hi")
    main.run(prop)
    System.out.flush()
  }
end WorkerTest
