import sbt.*

import sbt.internal.util.{ Appender, ConsoleAppender, ConsoleOut }
import sbt.util.{ Level, LogExchange }
import java.io.{ FileWriter, PrintWriter }

object Commands {
  lazy val markTask = taskKey[Unit]("Writes a marker file into the project's base directory")

  lazy val orphanTask = taskKey[Unit]("Declared but never defined, so it selects no tasks")

  val runAgg = Command.command("runAgg"): (st: State) =>
    Project.extract(st).runAggregated(markTask, st)

  val runAggScoped = Command.command("runAggScoped"): (st: State) =>
    Project.extract(st).runAggregated(LocalProject("projA") / markTask, st)

  /**
   * Runs `f` with an appender bound to the global logger, returning what it wrote.
   * `state.log` is `globalLogging.full`, created in `LoggerContext.globalContext`,
   * which is the context `LogExchange` binds into.
   *
   * The appender is deliberately left bound. `LogExchange.unbindLoggerAppenders`
   * delegates to `LoggerContext.clearAppenders`, which drops *every* appender on
   * the logger - sbt's own console appender included - and `LoggerContext` has no
   * per-appender removal. Unbinding here would silence the rest of the session,
   * and for the same reason `pw` is left open rather than closed.
   */
  private def capturingLog(st: State, file: File, level: Level.Value)(
      f: State => State
  ): (State, String) =
    val pw = new PrintWriter(new FileWriter(file), true)
    val appender: Appender =
      ConsoleAppender(s"i1210-${file.getName}", ConsoleOut.printWriterOut(pw), false)
    LogExchange.bindLoggerAppenders(st.globalLogging.full.name, Seq(appender -> level))
    val st1 = f(st)
    pw.flush()
    (st1, IO.read(file))

  val probeWarn = Command.command("probeWarn"): (st: State) =>
    val probe = "i1210 probe warning"
    val (st1, captured) = capturingLog(st, st.baseDir / "captured.log", Level.Warn): s =>
      s.log.warn(probe)
      s
    if !captured.contains(probe) then
      sys.error(s"global logger appender captured nothing; file held: [$captured]")
    st1

  val runAggOrphan = Command.command("runAggOrphan"): (st: State) =>
    val (st1, captured) = capturingLog(st, st.baseDir / "orphan.log", Level.Info): s =>
      Project.extract(s).runAggregated(orphanTask, s)
    if !captured.contains("selected no tasks to aggregate") then
      sys.error(s"expected a no-tasks warning; captured: [$captured]")
    if captured.contains("elapsed time") then
      sys.error(s"an empty aggregated run should not report success; captured: [$captured]")
    st1

  val runTaskUndefined = Command.command("runTaskUndefined"): (st: State) =>
    val outcome =
      try Right(Project.extract(st).runTask(orphanTask, st))
      catch case e: RuntimeException => Left(e.getMessage)
    val message = outcome match
      case Left(m)  => m
      case Right(_) => sys.error("expected runTask to fail on an undefined key")
    if !message.contains("orphanTask") || !message.contains("is undefined") then
      sys.error(s"runTask error should name the key: [$message]")
    st
}
