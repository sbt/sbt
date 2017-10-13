/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.Closeable
import sbt.util.Logger
import Def.{ ScopedKey, Classpath }
import sbt.internal.util.complete._
import java.io.File
import scala.util.Try

abstract class BackgroundJobService extends Closeable {

  /**
   * Launch a background job which is a function that runs inside another thread;
   *  killing the job will interrupt() the thread. If your thread blocks on a process,
   *  then you should get an InterruptedException while blocking on the process, and
   *  then you could process.destroy() for example.
   */
  def runInBackground(spawningTask: ScopedKey[_], state: State)(
      start: (Logger, File) => Unit): JobHandle

  /** Same as shutown. */
  def close(): Unit

  /** Shuts down all background jobs. */
  def shutdown(): Unit
  def jobs: Vector[JobHandle]
  def stop(job: JobHandle): Unit

  def waitForTry(job: JobHandle): Try[Unit] = {
    // This implementation is provided only for backward compatibility.
    Try(waitFor(job))
  }

  def waitFor(job: JobHandle): Unit

  /** Copies classpath to temporary directories. */
  def copyClasspath(products: Classpath, full: Classpath, workingDirectory: File): Classpath
}

object BackgroundJobService {
  private[sbt] def jobIdParser: (State, Seq[JobHandle]) => Parser[Seq[JobHandle]] = {
    import DefaultParsers._
    (state, handles) =>
      {
        val stringIdParser: Parser[Seq[String]] = Space ~> token(
          NotSpace examples handles.map(_.id.toString).toSet,
          description = "<job id>").+
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
}
