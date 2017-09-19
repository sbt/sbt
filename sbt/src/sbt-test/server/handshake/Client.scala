package example

import java.net.{ URI, Socket, InetAddress, SocketException }
import sbt.io._
import sbt.io.syntax._
import java.io.File
import sjsonnew.support.scalajson.unsafe.{ Parser, Converter, CompactPrinter }
import sjsonnew.shaded.scalajson.ast.unsafe.{ JValue, JObject, JString }

object Client extends App {
  val host = "127.0.0.1"
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

  def getPort: Int = {
    val portfile = baseDirectory / "project" / "target" / "active.json"
    val json: JValue = Parser.parseFromFile(portfile).get
    json match {
      case JObject(fields) =>
        (fields find { _.field == "uri" } map { _.value }) match {
          case Some(JString(value)) => 
            val u = new URI(value)
            u.getPort
          case _                    =>
            sys.error("json doesn't uri field that is JString")
        }
      case _ => sys.error("json doesn't have uri field")
    }
  }

  def getConnection: Socket =
    try {
      new Socket(InetAddress.getByName(host), getPort)
    } catch {
      case _ =>
        Thread.sleep(1000)
        getConnection
    }
}
