/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.lang.reflect.Method
import scala.annotation.unused

sealed trait ScriptedRun {
  final def run(
      resourceBaseDirectory: File,
      bufferLog: Boolean,
      tests: Seq[String],
      launcherJar: File,
      javaCommand: String,
      launchOpts: Seq[String],
      prescripted: java.util.List[File],
      instances: Int,
  ): Unit = {
    try {
      invoke(
        resourceBaseDirectory,
        bufferLog,
        tests.toArray,
        launcherJar,
        javaCommand,
        launchOpts.toArray,
        prescripted,
        instances,
      )
      ()
    } catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
  }

  protected def invoke(
      resourceBaseDirectory: File,
      bufferLog: java.lang.Boolean,
      tests: Array[String],
      launcherJar: File,
      javaCommand: String,
      launchOpts: Array[String],
      prescripted: java.util.List[File],
      instances: java.lang.Integer,
  ): AnyRef

}

object ScriptedRun {

  def of(scriptedTests: AnyRef, batchExecution: Boolean): ScriptedRun = {
    val fCls = classOf[File]
    val bCls = classOf[Boolean]
    val asCls = classOf[Array[String]]
    val sCls = classOf[String]
    val lfCls = classOf[java.util.List[File]]
    val iCls = classOf[Int]

    val clazz = scriptedTests.getClass
    if (batchExecution)
      try new RunInParallelV2(
        scriptedTests,
        clazz.getMethod("runInParallel", fCls, bCls, asCls, fCls, sCls, asCls, lfCls, iCls)
      )
      catch {
        case _: NoSuchMethodException =>
          new RunInParallelV1(
            scriptedTests,
            clazz.getMethod("runInParallel", fCls, bCls, asCls, fCls, asCls, lfCls, iCls)
          )
      }
    else
      try new RunV2(
        scriptedTests,
        clazz.getMethod("run", fCls, bCls, asCls, fCls, sCls, asCls, lfCls)
      )
      catch {
        case _: NoSuchMethodException =>
          new RunV1(scriptedTests, clazz.getMethod("run", fCls, bCls, asCls, fCls, asCls, lfCls))
      }
  }

  private class RunV1(scriptedTests: AnyRef, run: Method) extends ScriptedRun {
    override protected def invoke(
        resourceBaseDirectory: File,
        bufferLog: java.lang.Boolean,
        tests: Array[String],
        launcherJar: File,
        @unused javaCommand: String,
        launchOpts: Array[String],
        prescripted: java.util.List[File],
        @unused instances: java.lang.Integer,
    ): AnyRef =
      run.invoke(
        scriptedTests,
        resourceBaseDirectory,
        bufferLog,
        tests,
        launcherJar,
        launchOpts,
        prescripted,
      )
  }

  private class RunInParallelV1(scriptedTests: AnyRef, runInParallel: Method) extends ScriptedRun {
    override protected def invoke(
        resourceBaseDirectory: File,
        bufferLog: java.lang.Boolean,
        tests: Array[String],
        launcherJar: File,
        @unused javaCommand: String,
        launchOpts: Array[String],
        prescripted: java.util.List[File],
        instances: Integer,
    ): AnyRef =
      runInParallel.invoke(
        scriptedTests,
        resourceBaseDirectory,
        bufferLog,
        tests,
        launcherJar,
        launchOpts,
        prescripted,
        instances,
      )
  }

  private class RunV2(scriptedTests: AnyRef, run: Method) extends ScriptedRun {
    override protected def invoke(
        resourceBaseDirectory: File,
        bufferLog: java.lang.Boolean,
        tests: Array[String],
        launcherJar: File,
        javaCommand: String,
        launchOpts: Array[String],
        prescripted: java.util.List[File],
        @unused instances: java.lang.Integer,
    ): AnyRef =
      run.invoke(
        scriptedTests,
        resourceBaseDirectory,
        bufferLog,
        tests,
        launcherJar,
        javaCommand,
        launchOpts,
        prescripted,
      )
  }

  private class RunInParallelV2(scriptedTests: AnyRef, runInParallel: Method) extends ScriptedRun {
    override protected def invoke(
        resourceBaseDirectory: File,
        bufferLog: java.lang.Boolean,
        tests: Array[String],
        launcherJar: File,
        javaCommand: String,
        launchOpts: Array[String],
        prescripted: java.util.List[File],
        instances: Integer,
    ): AnyRef =
      runInParallel.invoke(
        scriptedTests,
        resourceBaseDirectory,
        bufferLog,
        tests,
        launcherJar,
        javaCommand,
        launchOpts,
        prescripted,
        instances,
      )
  }

}
