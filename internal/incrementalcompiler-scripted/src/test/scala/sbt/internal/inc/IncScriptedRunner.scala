package sbt.internal.inc

import java.io.File

import sbt.internal.scripted.ScriptedRunnerImpl

class IncScriptedRunner {

  def run(resourceBaseDirectory: File, bufferLog: Boolean, tests: Array[String]): Unit = {
    val handlers = new IncScriptedHandlers()
    ScriptedRunnerImpl.run(resourceBaseDirectory, bufferLog, tests, handlers);
  }

}