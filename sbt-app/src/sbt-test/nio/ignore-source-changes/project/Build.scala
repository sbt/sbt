package sbt

import sbt.Keys._
import sbt.nio.Keys._
import sbt.internal.util._
import sbt.util.Level

object Build extends AutoPlugin {
  val autoImport = Build

  override def trigger = allRequirements

  val awaitsWarn =
    settingKey[Boolean]("Whether we're checking for warn or not")
  val checkBuildSourcesWarn =
    taskKey[Unit]("Checks whether there was a warning or not")

  class CapturingMiniLogger extends MiniLogger {
    var warnCaptured = false
    override def log[T](level: Level.Value, message: ObjectEvent[T]): Unit = ()
    override def log(level: Level.Value, message: => String): Unit =
      if (level == Level.Warn && message.contains("build source files have changed"))
        warnCaptured = true
  }

  override def buildSettings: Seq[Setting[?]] = Seq(
    awaitsWarn := false,
    checkBuildSourcesWarn := Def.uncached {
      val st = state.value
      val capturing = new CapturingMiniLogger
      val originalFull = st.globalLogging.full
      val capturingLogger = new ManagedLogger(
        originalFull.name,
        originalFull.channelName,
        originalFull.execId,
        capturing
      )
      val newLogging = st.globalLogging.copy(full = capturingLogger)
      val newState = st.copy(globalLogging = newLogging)

      val extractedSt = Project.extract(newState)
      extractedSt.runTask(checkBuildSources, newState)

      val warnCaptured = capturing.warnCaptured
      val awaitsWarnValue = extractedSt.get(awaitsWarn)
      if (warnCaptured != awaitsWarnValue)
        throw new Exception(
          s"Warn appeared: ${warnCaptured}; Expected: ${awaitsWarnValue}; See issue #6773"
        )
    }
  )
}
