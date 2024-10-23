package sbt.internal.worker

import java.nio.file.{ Path, Paths }
import java.net.URLClassLoader
import scala.collection.mutable

object WorkDispatchTest extends verify.BasicTestSuite:
  val originalOut = Console.out

  test("request rejects non-JSON RPC") {
    withDispatch(_ => ()): dispatch =>
      val either = dispatch.request("""{}""")
      assert(either.isLeft)
  }

  test("request rejects non-sbt/general RPC") {
    withDispatch(_ => ()): dispatch =>
      val either = dispatch.request("""{ "jsonrpc": "2.0",
"method": "unknown",
"params": {},
"id": 12 }""")
      assert(either.isLeft)
  }

  test("request sbt/general RPC returns result 0 when it succeeds") {
    val sb = mutable.StringBuilder()
    withDispatch(s => sb.append(s)): dispatch =>
      val either = dispatch.request(currentClasspathRequest("hi"))
      assert(either.isRight)
      val lines = sb.toString().linesIterator.toList
      val expected = """{"jsonrpc":"2.0","id":"12","result":0}"""
      assert(lines(1).trim() == expected)
  }

  test("request sbt/general RPC forwards stdout to notify/console") {
    val sb = mutable.StringBuilder()
    withDispatch(s => sb.append(s)): dispatch =>
      val either = dispatch.request(currentClasspathRequest("hi"))
      val lines = sb.toString().linesIterator.toList
      val expected =
        """{"jsonrpc":"2.0","method":"notify/console","params":{"ref":"12","stdout":"hi\n"}}"""
      assert(lines.head == expected)
  }

  test("request sbt/general RPC error") {
    val sb = mutable.StringBuilder()
    withDispatch(s => sb.append(s)): dispatch =>
      val either = dispatch.request(currentClasspathRequest("boom"))
      val lines = sb.toString().linesIterator.toList
      val expected = """{"jsonrpc":"2.0","id":"12","error":{"code":-33000,"message":"boom"}}"""
      assert(lines.head == expected)
  }

  def currentClasspathRequest(arg: String): String =
    val files = currentClasspath
      .map(s => s"""{ "path": "$s", "digest": "aa" }""")
      .mkString(",")
    s"""{ "jsonrpc": "2.0",
  "method": "sbt/general",
  "params": {
    "main_class": "example.main",
    "classpath": [
      $files
    ],
    "args": ["$arg"]
  },
  "id": "12" }"""

  def currentClasspath: List[Path] =
    val cl = Thread.currentThread().getContextClassLoader()
    // this forwards the current classpath to the worker
    cl match
      case cl: URLClassLoader =>
        cl.getURLs.toList
          .map(u => Paths.get(u.getFile()))
      case _ => sys.error("URLClassLoader expected")

  def withDispatch[A1](stdout: String => Unit)(f: WorkDispatch => A1): A1 =
    val dispatch = WorkDispatch(stdout = stdout)
    f(dispatch)
end WorkDispatchTest
