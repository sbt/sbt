package example

import java.net.{ URI, Socket, InetAddress, SocketException }
import sbt.io._
import sbt.io.syntax._
import java.io.File

object Client extends App {
  val host = "127.0.0.1"
  val port = 5123
  val delimiter: Byte = '\n'.toByte

  println("hello")
  Thread.sleep(1000)

  val connection = getConnection
  val out = connection.getOutputStream
  val in = connection.getInputStream

  out.write("""{ "type": "ExecCommand", "commandLine": "exit" }""".getBytes("utf-8"))
  out.write(delimiter.toInt)
  out.flush

  val baseDirectory = new File(args(0))
  IO.write(baseDirectory / "ok.txt", "ok")

  def getConnection: Socket =
    try {
      new Socket(InetAddress.getByName(host), port)
    } catch {
      case _ =>
        Thread.sleep(1000)
        getConnection
    }
}
