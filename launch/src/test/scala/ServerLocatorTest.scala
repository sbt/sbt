package xsbt.boot

import java.io.{ File, InputStream }
import java.net.URL
import java.util.Properties
import xsbti._
import org.specs2._
import mutable.Specification
import LaunchTest._
import sbt.IO.{ createDirectory, touch, withTemporaryDirectory }
import java.net.URI

object ServerLocatorTest extends Specification {
  "ServerLocator" should {
    // TODO - Maybe use scalacheck to randomnly generate URIs
    "read and write server URI properties" in {
      withTemporaryDirectory { dir =>
        val propFile = new File(dir, "server.properties")
        val expected = new java.net.URI("http://localhost:8080")
        ServerLocator.writeProperties(propFile, expected)
        ServerLocator.readProperties(propFile) must equalTo(Some(expected))
      }
    }
    "detect listening ports" in {
      val serverSocket = new java.net.ServerSocket(0)
      object serverThread extends Thread {
        override def run(): Unit = {
          // Accept one connection.
          val result = serverSocket.accept()
          result.close()
          serverSocket.close()
        }
      }
      serverThread.start()
      val uri = new java.net.URI(s"http://${serverSocket.getInetAddress.getHostAddress}:${serverSocket.getLocalPort}")
      ServerLocator.isReachable(uri) must beTrue
    }
  }
  "ServerLauncher" should {
    "detect start URI from reader" in {
      val expected = new java.net.URI("http://localhost:8080")
      val input = s"""|Some random text
                      |to start the server
                      |${ServerApplication.SERVER_SYNCH_TEXT}${expected.toASCIIString}
                      |Some more output.""".stripMargin
      val inputStream = new java.io.BufferedReader(new java.io.StringReader(input))
      val result = try ServerLauncher.readUntilSynch(inputStream)
      finally inputStream.close()
      result must equalTo(Some(expected))
    }
  }
}