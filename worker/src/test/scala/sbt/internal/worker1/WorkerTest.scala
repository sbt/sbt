package sbt.internal.worker1

import sbt.io.IO

object WorkerTest extends verify.BasicTestSuite:
  val main = WorkerMain()

  test("process") {
    val u0 = IO.classLocationPath(classOf[example.Hello]).toUri()
    val u1 = IO.classLocationPath(classOf[scala.quoted.Quotes]).toUri()
    val u2 = IO.classLocationPath(classOf[scala.collection.immutable.List[?]]).toUri()
    val cp =
      s"""[{ "path": "${u0}", "digest": "" }, { "path": "${u1}", "digest": "" }, { "path": "${u2}", "digest": "" }]"""
    val runInfo =
      s"""{ "jvm": true, "jvmRunInfo": { "args": ["hi"], "classpath": $cp, "mainClass": "example.Hello" } }"""
    val json = s"""{ "jsonrpc": "2.0", "id": 1, "method": "run", "params": $runInfo }"""
    main.process(json)
  }
end WorkerTest
