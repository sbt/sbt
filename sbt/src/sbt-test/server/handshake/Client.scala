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

  lazy val connection = getConnection
  lazy val out = connection.getOutputStream
  lazy val in = connection.getInputStream

  val t = getToken
  val msg0 = s"""{ "type": "InitCommand", "token": "$t" }"""

  writeLine(s"Content-Length: ${ msg0.size + 2 }")
  writeLine("Content-Type: application/sbt-x1")
  writeLine("")
  writeLine(msg0)
  out.flush

  writeLine("Content-Length: 49")
  writeLine("Content-Type: application/sbt-x1")
  writeLine("")
            // 12345678901234567890123456789012345678901234567890
  writeLine("""{ "type": "ExecCommand", "commandLine": "exit" }""")
  writeLine("")
  out.flush

  val baseDirectory = new File(args(0))
  IO.write(baseDirectory / "ok.txt", "ok")

  def getToken: String = {
    val tokenfile = new File(getTokenFileUri)
    val json: JValue = Parser.parseFromFile(tokenfile).get
    json match {
      case JObject(fields) =>
        (fields find { _.field == "token" } map { _.value }) match {
          case Some(JString(value)) => value
          case _                    =>
            sys.error("json doesn't token field that is JString")
        }
      case _ => sys.error("json doesn't have token field")
    }
  }

  def getTokenFileUri: URI = {
    val portfile = baseDirectory / "project" / "target" / "active.json"
    val json: JValue = Parser.parseFromFile(portfile).get
    json match {
      case JObject(fields) =>
        (fields find { _.field == "tokenfileUri" } map { _.value }) match {
          case Some(JString(value)) => new URI(value)
          case _                    =>
            sys.error("json doesn't tokenfile field that is JString")
        }
      case _ => sys.error("json doesn't have tokenfile field")
    }
  }

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

  def writeLine(s: String): Unit = {
    if (s != "") {
      out.write(s.getBytes("UTF-8"))
    }
    writeEndLine
  }

  def writeEndLine(): Unit = {
    val retByte: Byte = '\r'.toByte
    val delimiter: Byte = '\n'.toByte

    out.write(retByte.toInt)
    out.write(delimiter.toInt)
    out.flush
  }
}
