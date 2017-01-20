package sbt

import java.io.Closeable
import sbt.util.Logger
import Def.ScopedKey
import sbt.internal.util.complete._

abstract class BackgroundJobService extends Closeable {
  /**
   * Launch a background job which is a function that runs inside another thread;
   *  killing the job will interrupt() the thread. If your thread blocks on a process,
   *  then you should get an InterruptedException while blocking on the process, and
   *  then you could process.destroy() for example.
   */
  def runInBackground(spawningTask: ScopedKey[_], state: State)(start: (Logger) => Unit): JobHandle
  def close: Unit = ()
  def jobs: Vector[JobHandle]
  def stop(job: JobHandle): Unit
  def waitFor(job: JobHandle): Unit
}

object BackgroundJobService {
  def jobIdParser: (State, Seq[JobHandle]) => Parser[Seq[JobHandle]] = {
    import DefaultParsers._
    (state, handles) => {
      val stringIdParser: Parser[Seq[String]] = Space ~> token(NotSpace examples handles.map(_.id.toString).toSet, description = "<job id>").+
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
