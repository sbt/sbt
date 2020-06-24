package testpkg

import java.io.{ InputStream, PrintStream }
import sbt.internal.client.NetworkClient

object ClientTest extends AbstractServerTest {
  override val testDirectory: String = "client"
  object NullInputStream extends InputStream {
    override def read(): Int = {
      try this.synchronized(this.wait)
      catch { case _: InterruptedException => }
      -1
    }
  }
  val NullPrintStream = new PrintStream(_ => {}, false)
  private def client(args: String*) =
    NetworkClient.client(
      testPath.toFile,
      args.toArray,
      NullInputStream,
      NullPrintStream,
      NullPrintStream,
      false
    )
  test("exit success") { c =>
    assert(client("willSucceed") == 0)
  }
  test("exit failure") { _ =>
    assert(client("willFail") == 1)
  }
  test("two commands") { _ =>
    assert(client("compile;willSucceed") == 0)
  }
  test("two commands with failing second") { _ =>
    assert(client("compile;willFail") == 1)
  }
  test("two commands with leading failure") { _ =>
    assert(client("willFail;willSucceed") == 1)
  }
  test("three commands") { _ =>
    assert(client("compile;clean;willSucceed") == 0)
  }
  test("three commands with middle failure") { _ =>
    assert(client("compile;willFail;willSucceed") == 1)
  }
}
