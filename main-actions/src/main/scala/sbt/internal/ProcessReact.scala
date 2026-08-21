package sbt
package internal

import org.scalasbt.shadedgson.com.google.gson.{ JsonObject, JsonParser, JsonSyntaxException }
import sbt.internal.inc.CompileFailed
import sbt.util.Logger
import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration.Duration
import scala.sys.process.Process
import scala.util.control.NonFatal

private[sbt] abstract class ProcessReact[A1](
    id: Long,
    log: Logger,
    process: Process
) extends WorkerResponseListener:
  protected val promise: Promise[A1] = Promise()

  def processNotification(o: JsonObject): Unit
  def processResponse(o: JsonObject): Unit
  override def apply(line: String): Unit =
    try
      val o = JsonParser.parseString(line).getAsJsonObject()
      if o.has("id") then
        val resId = o.getAsJsonPrimitive("id").getAsLong()
        if resId == id then
          if promise.isCompleted then ()
          else if o.has("error") then
            val err = o.getAsJsonObject("error")
            val code = err.getAsJsonPrimitive("code").getAsLong()
            val message = Option(err.getAsJsonPrimitive("message"))
              .map(_.getAsString())
              .getOrElse(s"worker error (code $code)")
            code match
              case 1009 =>
                promise.tryFailure(new CompileFailed(Array.empty, message, Array.empty))
              case _ => promise.tryFailure(new RuntimeException(message))
          else
            try processResponse(o)
            catch case NonFatal(e) => promise.tryFailure(e)
        else ()
      else if o.has("re") && o.has("method") then
        val resId = o.getAsJsonPrimitive("re").getAsLong()
        if resId == id then processNotification(o)
        else ()
      else ()
    catch
      case _: JsonSyntaxException => log.info(line)
      case NonFatal(_)            => ()

  override def notifyExit(p: Process): Unit =
    if (p eq process) && !process.isAlive() && !promise.isCompleted then
      val exitCode = process.exitValue()
      promise.tryFailure(new RuntimeException(s"worker exited with code $exitCode"))

  def blockForResponse(): A1 =
    Await.result(promise.future, Duration.Inf)
end ProcessReact
