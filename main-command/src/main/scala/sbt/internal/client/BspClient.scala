package sbt.internal.client

import java.io.{ File, InputStream, OutputStream }
import java.net.Socket

import sbt.Exit
import sbt.io.syntax._
import sbt.protocol.ClientSocket

import scala.sys.process.Process
import scala.util.control.NonFatal

class BspClient private (sbtServer: Socket) {

  private def transferTo(input: InputStream, output: OutputStream): Unit = {
    val buffer = Array.ofDim[Byte](1024)
    while (true) {
      val size = input.read(buffer)
      output.write(buffer, 0, size)
      output.flush()
    }
  }

  private def run(): Exit = {
    try {
      val redirection = new Thread {
        override def run(): Unit = transferTo(sbtServer.getInputStream, System.out)
      }

      redirection.start()
      transferTo(System.in, sbtServer.getOutputStream)

      Exit(0)
    } catch {
      case NonFatal(_) => Exit(1)
    }
  }
}

object BspClient {
  def run(configuration: xsbti.AppConfiguration): Exit = {
    val baseDirectory = configuration.baseDirectory
    val portFile = baseDirectory / "project" / "target" / "active.json"
    try {
      if (!portFile.exists) {
        forkServer(baseDirectory, portFile)
      }
      val (socket, _) = ClientSocket.socket(portFile)
      new BspClient(socket).run()
    } catch {
      case NonFatal(_) => Exit(1)
    }
  }

  /**
   * Forks another instance of sbt in the background.
   * This instance must be shutdown explicitly via `sbt -client shutdown`
   */
  def forkServer(baseDirectory: File, portfile: File): Unit = {
    val args = List[String]()
    val launchOpts = List("-Xms2048M", "-Xmx2048M", "-Xss2M")

    val launcherJarString = sys.props.get("java.class.path") match {
      case Some(cp) =>
        cp.split(File.pathSeparator)
          .headOption
          .getOrElse(sys.error("launcher JAR classpath not found"))
      case _ => sys.error("property java.class.path expected")
    }

    val cmd = "java" :: launchOpts ::: "-jar" :: launcherJarString :: args
    val process = Process(cmd, baseDirectory).run()

    while (process.isAlive() && !portfile.exists) Thread.sleep(100)

    if (!process.isAlive()) sys.error("sbt server exited")
  }
}
