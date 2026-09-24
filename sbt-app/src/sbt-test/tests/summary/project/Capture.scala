package sbt
package recaplog

import sbt.{ *, given }
import java.io.{ File, FileWriter, PrintWriter }
import java.util.concurrent.atomic.AtomicReference
import sbt.internal.util.{ Appender, ConsoleAppender, ConsoleOut }
import sbt.util.{ Level, Logger, LoggerContext }

/**
 * `captureLog <file>` attaches an appender to the global logger; the
 * output target is swappable so the command can be re-run per block.
 * `captureTestLog <file>` instead points `captureTestResultLogger`
 * (wired as `Global / testResultLogger`) at a file, since `test`'s own
 * output goes through a task-scoped logger `captureLog` can't reach.
 */
object Capture:
  private val appenderName = "global-capture"
  private val currentWriter = new AtomicReference[PrintWriter]
  private val currentOut = new AtomicReference[ConsoleOut]

  private val properties: ConsoleAppender.Properties = new ConsoleAppender.Properties:
    def isAnsiSupported: Boolean = false
    def isColorEnabled: Boolean = false
    def out: ConsoleOut = currentOut.get

  private val appender: Appender =
    new ConsoleAppender(appenderName, properties, ConsoleAppender.noSuppressedMessage)

  val captureLog: Command = Command.single("captureLog") { (s, fileName) =>
    val f = s.baseDir / "target" / fileName
    f.getParentFile.mkdirs()
    val writer = new PrintWriter(new FileWriter(f, true))
    currentOut.set(ConsoleOut.printWriterOut(writer))
    val previous = currentWriter.getAndSet(writer)
    if previous != null then previous.close()

    val ctx = LoggerContext.globalContext
    val loggerName = s.globalLogging.full.name
    if !ctx.appenders(loggerName).exists(_.name == appenderName) then
      ctx.addAppender(loggerName, appender -> Level.Info)
    s
  }

  private val testWriter = new AtomicReference[PrintWriter]

  val captureTestLog: Command = Command.single("captureTestLog") { (s, fileName) =>
    val f = s.baseDir / "target" / fileName
    f.getParentFile.mkdirs()
    val writer = new PrintWriter(new FileWriter(f, true))
    val previous = testWriter.getAndSet(writer)
    if previous != null then previous.close()
    s
  }

  private def tee(base: Logger, w: PrintWriter): Logger = new Logger:
    def trace(t: => Throwable): Unit = base.trace(t)
    def success(message: => String): Unit = base.success(message)
    def log(level: Level.Value, message: => String): Unit =
      base.log(level, message)
      if level.compare(Level.Info) >= 0 then
        w.println(message)
        w.flush()

  val captureTestResultLogger: TestResultLogger = TestResultLogger { (log, results, taskName, cached) =>
    val target = Option(testWriter.get).map(tee(log, _)).getOrElse(log)
    TestResultLogger.SilentWhenNoTests.run(target, results, taskName, cached)
  }
end Capture
