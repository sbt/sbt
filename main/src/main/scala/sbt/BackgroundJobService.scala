/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.Closeable

import sbt.util.Logger
import Def.{ Classpath, ScopedKey }
import sbt.internal.util.complete._
import java.io.File

import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

abstract class BackgroundJobService extends Closeable {

  /**
   * Launch a background job which is a function that runs inside another thread;
   *  killing the job will interrupt() the thread. If your thread blocks on a process,
   *  then you should get an InterruptedException while blocking on the process, and
   *  then you could process.destroy() for example.
   */
  def runInBackground(spawningTask: ScopedKey[_], state: State)(
      start: (Logger, File) => Unit
  ): JobHandle

  /**
   * Launch a background job which is a function that runs inside another thread.
   *  Differs from [[BackgroundJobService.runInBackground]] in that the start task
   *  provides both an optional classloader and the work to run in the background thread.
   *  The service should close the classloader when the task completes.
   *  killing the job will interrupt() the thread. If your thread blocks on a process,
   *  then you should get an InterruptedException while blocking on the process, and
   *  then you could process.destroy() for example.
   */
  private[sbt] def runInBackgroundWithLoader(spawningTask: ScopedKey[_], state: State)(
      start: (Logger, File) => (Option[ClassLoader], () => Unit)
  ): JobHandle = runInBackground(spawningTask, state) { (logger, file) =>
    start(logger, file)._2.apply()
  }

  /** Same as shutdown. */
  def close(): Unit

  /** Shuts down all background jobs. */
  def shutdown(): Unit
  def jobs: Vector[JobHandle]
  def stop(job: JobHandle): Unit

  /**
   * Delegate to waitFor but catches any exceptions and returns the result in an instance of `Try`.
   * @param job the job to wait for
   * @return the result of waiting for the job to complete.
   */
  def waitForTry(job: JobHandle): Try[Unit] = {
    try Success(waitFor(job))
    catch {
      case NonFatal(e) =>
        try stop(job)
        catch { case NonFatal(_) => }
        Failure(e)
    }
  }

  def waitFor(job: JobHandle): Unit

  /** Copies classpath to temporary directories. */
  def copyClasspath(products: Classpath, full: Classpath, workingDirectory: File): Classpath

  private[sbt] def copyClasspath(
      products: Classpath,
      full: Classpath,
      workingDirectory: File,
      hashContents: Boolean
  ): Classpath = copyClasspath(products, full, workingDirectory)
}

object BackgroundJobService {
  private[sbt] def jobIdParser: (State, Seq[JobHandle]) => Parser[Seq[JobHandle]] = {
    import DefaultParsers._
    (state, handles) => {
      val stringIdParser: Parser[Seq[String]] = Space ~> token(
        NotSpace examples handles.map(_.id.toString).toSet,
        description = "<job id>"
      ).+
      stringIdParser.map { strings =>
        strings.map(Integer.parseInt(_)).flatMap(id => handles.find(_.id == id))
      }
    }
  }
}

abstract class JobHandle {
  def id: Long
  def humanReadableName: String
  def spawningTask: ScopedKey[_]
  def isAutoCancel: Boolean
}
