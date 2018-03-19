package coursier.cli

import java.io.{BufferedReader, File, InputStream, InputStreamReader, PipedInputStream, PipedOutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

import scala.util.control.NonFatal

object SparkOutputHelper {

  def outputInspectThread(
    name: String,
    from: InputStream,
    to: PrintStream,
    handlers: Seq[String => Unit]
  ) = {

    val t = new Thread {
      override def run() = {
        val in = new BufferedReader(new InputStreamReader(from))
        var line: String = null
        while ({
          line = in.readLine()
          line != null
        }) {
          to.println(line)
          handlers.foreach(_(line))
        }
      }
    }

    t.setName(name)
    t.setDaemon(true)

    t
  }


  def handleOutput(yarnAppFileOpt: Option[File], maxIdleTimeOpt: Option[Int]): Unit = {

    var handlers = Seq.empty[String => Unit]
    var threads = Seq.empty[Thread]

    for (yarnAppFile <- yarnAppFileOpt) {

      val Pattern = ".*Application report for ([^ ]+) .*".r

      @volatile var written = false
      val lock = new AnyRef
      def handleMessage(s: String): Unit =
        if (!written)
          s match {
            case Pattern(id) =>
              lock.synchronized {
                if (!written) {
                  println(s"Detected YARN app ID $id")
                  Option(yarnAppFile.getParentFile).foreach(_.mkdirs())
                  Files.write(yarnAppFile.toPath, id.getBytes(UTF_8))
                  written = true
                }
              }
            case _ =>
          }

      val f = { line: String =>
        try handleMessage(line)
        catch {
          case NonFatal(_) =>
        }
      }

      handlers = handlers :+ f
    }

    for (maxIdleTime <- maxIdleTimeOpt if maxIdleTime > 0) {

      @volatile var lastMessageTs = -1L

      def updateLastMessageTs() = {
        lastMessageTs = System.currentTimeMillis()
      }

      val checkThread = new Thread {
        override def run() =
          try {
            while (true) {
              lastMessageTs = -1L
              Thread.sleep(maxIdleTime * 1000L)
              if (lastMessageTs < 0) {
                Console.err.println(s"No output from spark-submit for more than $maxIdleTime s, exiting")
                sys.exit(1)
              }
            }
          } catch {
            case t: Throwable =>
              Console.err.println(s"Caught $t in check spark-submit output thread!")
              throw t
          }
      }

      checkThread.setName("check-spark-submit-output")
      checkThread.setDaemon(true)

      threads = threads :+ checkThread

      val f = { line: String =>
        updateLastMessageTs()
      }

      handlers = handlers :+ f
    }

    def createThread(name: String, replaces: PrintStream, install: PrintStream => Unit): Thread = {
      val in  = new PipedInputStream
      val out = new PipedOutputStream(in)
      install(new PrintStream(out))
      outputInspectThread(name, in, replaces, handlers)
    }

    if (handlers.nonEmpty) {
      threads = threads ++ Seq(
        createThread("inspect-out", System.out, System.setOut),
        createThread("inspect-err", System.err, System.setErr)
      )

      threads.foreach(_.start())
    }
  }
}
