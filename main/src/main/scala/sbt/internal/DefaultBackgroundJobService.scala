/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.{ Closeable, File, FileInputStream, IOException }
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{ FileVisitResult, Files, Path, SimpleFileVisitor }
import java.security.{ DigestInputStream, MessageDigest }
import java.util.concurrent.{ ConcurrentHashMap, TimeUnit }
import java.util.concurrent.atomic.{ AtomicLong, AtomicReference }

import sbt.Def.{ Classpath, ScopedKey, Setting }
import sbt.ProjectExtra.extract
import sbt.Scope.GlobalScope
import sbt.SlashSyntax0.given
import sbt.internal.inc.classpath.ClasspathFilter
import sbt.internal.util.{ Attributed, ManagedLogger }
import sbt.io.syntax._
import sbt.io.{ Hash, IO }
import sbt.util.{ Level, Logger }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try
import sbt.util.LoggerContext
import java.util.concurrent.TimeoutException
import xsbti.FileConverter
import xsbti.HashedVirtualFileRef

/**
 * Interface between sbt and a thing running in the background.
 */
private[sbt] abstract class BackgroundJob {
  def humanReadableName: String
  @deprecated("Use awaitTermination that takes a duration argument", "1.4.0")
  final def awaitTermination(): Unit = awaitTermination(Duration.Inf)
  def awaitTermination(duration: Duration): Unit

  /** This waits till the job ends, and returns inner error via `Try`. */
  @deprecated("Use awaitTerminationTry that takes a duration argument", "1.4.0")
  final def awaitTerminationTry(): Try[Unit] = {
    // This implementation is provided only for backward compatibility.
    Try(awaitTermination(Duration.Inf))
  }

  /** This waits till the job ends, and returns inner error via `Try`. */
  def awaitTerminationTry(duration: Duration): Try[Unit] = {
    // This implementation is provided only for backward compatibility.
    Try(awaitTermination(duration))
  }

  def shutdown(): Unit

  // this should be true on construction and stay true until
  // the job is complete
  def isRunning(): Boolean

  // called after stop or on spontaneous exit, closing the result
  // removes the listener
  def onStop(listener: () => Unit)(using ex: ExecutionContext): Closeable

  // do we need this or is the spawning task good enough?
  // def tags: SomeType
}

private[sbt] abstract class AbstractJobHandle extends JobHandle {
  override def toString =
    s"JobHandle(${id}, ${humanReadableName}, ${Def.showFullKey.show(spawningTask)})"
}

private[sbt] abstract class AbstractBackgroundJobService extends BackgroundJobService {
  private val nextId = new AtomicLong(1)
  private val pool = new BackgroundThreadPool()
  private val context = LoggerContext()
  // EC for onStop handler below
  given ExecutionContext =
    ExecutionContext.fromExecutor(pool.executor)

  private[sbt] def serviceTempDirBase: File
  private[sbt] def useLog4J: Boolean
  private val serviceTempDirRef = new AtomicReference[File]
  private def serviceTempDir: File = serviceTempDirRef.synchronized {
    serviceTempDirRef.get match {
      case null =>
        val dir = IO.createUniqueDirectory(serviceTempDirBase)
        serviceTempDirRef.set(dir)
        dir
      case s => s
    }
  }
  // hooks for sending start/stop events
  protected def onAddJob(@deprecated("unused", "") job: JobHandle): Unit = ()
  protected def onRemoveJob(@deprecated("unused", "") job: JobHandle): Unit = ()

  // this mutable state could conceptually go on State except
  // that then every task that runs a background job would have
  // to be a command, so not sure what to do here.
  @volatile
  private final var jobSet = Set.empty[ThreadJobHandle]
  private def addJob(job: ThreadJobHandle): Unit = synchronized {
    onAddJob(job)
    jobSet += job
  }

  private def removeJob(job: ThreadJobHandle): Unit = synchronized {
    onRemoveJob(job)
    jobSet -= job
  }
  override def jobs: Vector[ThreadJobHandle] = jobSet.toVector

  final class ThreadJobHandle(
      override val id: Long,
      override val spawningTask: ScopedKey[?],
      val logger: ManagedLogger,
      val workingDirectory: File,
      val job: BackgroundJob
  ) extends AbstractJobHandle {
    def humanReadableName: String = job.humanReadableName

    job.onStop { () =>
      removeJob(this)
      IO.delete(workingDirectory)
      context.clearAppenders(logger.name)
    }
    addJob(this)
    override final def equals(other: Any): Boolean = other match {
      case handle: JobHandle if handle.id == id => true
      case _                                    => false
    }
    override final def hashCode(): Int = id.hashCode
  }

  private val unknownTask = TaskKey[Unit]("unknownTask", "Dummy value")
  // we use this if we deserialize a handle for a job that no longer exists
  private final class DeadHandle(override val id: Long, override val humanReadableName: String)
      extends AbstractJobHandle {
    override val spawningTask: ScopedKey[?] = unknownTask
  }

  def doRunInBackground(
      spawningTask: ScopedKey[?],
      state: State,
      start: (Logger, File) => BackgroundJob
  ): JobHandle = {
    val id = nextId.getAndIncrement()
    val extracted = Project.extract(state)
    val logger =
      LogManager.constructBackgroundLog(extracted.structure.data, state, context)(spawningTask)
    val workingDir = serviceTempDir / s"job-$id"
    IO.createDirectory(workingDir)
    val job =
      try {
        new ThreadJobHandle(id, spawningTask, logger, workingDir, start(logger, workingDir))
      } catch {
        case e: Throwable =>
          // TODO: Fix this
          // logger.close()
          throw e
      }
    job
  }

  override def runInBackground(spawningTask: ScopedKey[?], state: State)(
      start: (Logger, File) => Unit
  ): JobHandle = {
    pool.run(this, spawningTask, state)(start)
  }

  override private[sbt] def runInBackgroundWithLoader(spawningTask: ScopedKey[?], state: State)(
      start: (Logger, File) => (Option[ClassLoader], () => Unit)
  ): JobHandle = {
    pool.runWithLoader(this, spawningTask, state)(start)
  }

  override final def close(): Unit = shutdown()
  override def shutdown(): Unit = {
    val deadline = 10.seconds.fromNow
    while (jobSet.nonEmpty && !deadline.isOverdue) {
      jobSet.headOption.foreach { case handle: ThreadJobHandle @unchecked =>
        if (handle.job.isRunning()) {
          handle.job.shutdown()
          handle.job.awaitTerminationTry(10.seconds)
        }
        jobSet = jobSet - handle
      }
    }
    pool.close()
    Option(serviceTempDirRef.get).foreach(IO.delete)
  }

  private def withHandle(job: JobHandle)(f: ThreadJobHandle => Unit): Unit = job match {
    case handle: ThreadJobHandle @unchecked => f(handle)
    case _: DeadHandle @unchecked           => () // nothing to stop or wait for
    case other =>
      sys.error(
        s"BackgroundJobHandle does not originate with the current BackgroundJobService: $other"
      )
  }

  override def stop(job: JobHandle): Unit =
    withHandle(job)(_.job.shutdown())

  override def waitFor(job: JobHandle): Unit =
    withHandle(job)(_.job.awaitTermination(Duration.Inf))

  override def toString(): String = s"BackgroundJobService(jobs=${jobs.map(_.id).mkString})"

  /**
   * Copies products to the working directory, and the rest to the serviceTempDir of this service,
   * both wrapped in a stamp of the file contents.
   * This is intended to minimize the file copying and accumulation of the unused JAR file.
   * Since working directory is wiped out when the background job ends, the product JAR is deleted too.
   * Meanwhile, the rest of the dependencies are cached for the duration of this service.
   *
   * @param products the portion of the classpath that is generated by the task
   * @param full the entire classpath of the task
   * @param workingDirectory the directory into which jars and class files are copied
   * @param hashFileContents toggles whether or not the contents of each files should be hashed
   *                         to determine whether it has changed. When false, the last modified
   *                         time is used instead.
   *
   * @return a classpath pointing to jar and class files in the working directory
   */
  override private[sbt] def copyClasspath(
      products: Classpath,
      full: Classpath,
      workingDirectory: File,
      hashFileContents: Boolean,
      converter: FileConverter,
  ): Classpath = {
    def syncTo(
        dir: File
    )(source0: Attributed[HashedVirtualFileRef]): Attributed[HashedVirtualFileRef] = {
      val source1 = source0.data
      val source = converter.toPath(source1).toFile()
      val hash8 = Hash.toHex(Hash(source.toString)).take(8)
      val id: File => String = if (hashFileContents) hash else lastModified
      val dest = dir / hash8 / id(source) / source.getName
      if !dest.exists then
        if (source.isDirectory) IO.copyDirectory(source, dest)
        else IO.copyFile(source, dest)
      Attributed.blank(converter.toVirtualFile(dest.toPath))
    }
    val xs = (products.toVector map { syncTo(workingDirectory / "target") }) ++
      ((full diff products) map { syncTo(serviceTempDir / "target") })
    xs
  }

  /** An alternative to sbt.io.Hash that handles java.io.File being a directory. */
  private def hash(f: File): String = {
    val digest = MessageDigest.getInstance("SHA")
    val buffer = new Array[Byte](8192)
    Files.walkFileTree(
      f.toPath,
      new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          val dis = new DigestInputStream(new FileInputStream(file.toFile), digest)
          try {
            while (dis.read(buffer) >= 0) ()
            FileVisitResult.CONTINUE
          } catch {
            case _: IOException => FileVisitResult.TERMINATE
          } finally dis.close()
        }
      }
    )
    Hash.toHex(Hash(digest.digest)).take(8)
  }

  /**
   * Computes the last modified time of a file or the maximum last file of the contents of a
   * directory.
   *
   * @param f the file or directory for which we calculate the last modified time
   * @return the last modified time of the file or the maximum last modified time of the contents
   *         of the directory.
   */
  private def lastModified(f: File): String = {
    var lastModified = 0L
    Files.walkFileTree(
      f.toPath,
      new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          val lm = attrs.lastModifiedTime.toMillis
          if (lm > lastModified) lastModified = lm
          FileVisitResult.CONTINUE
        }
      }
    )
    lastModified.toString
  }

  /** Copies classpath to temporary directories. */
  override def copyClasspath(
      products: Classpath,
      full: Classpath,
      workingDirectory: File,
      converter: FileConverter,
  ): Classpath =
    copyClasspath(products, full, workingDirectory, hashFileContents = true, converter)

  private[sbt] def pauseChannelDuringJob(state: State, handle: JobHandle): Unit =
    currentChannel(state) match
      case Some(channel) =>
        handle match
          case t: ThreadJobHandle =>
            val level = channel.logLevel
            channel.setLevel(Level.Error)
            channel.pause()
            t.job.onStop: () =>
              channel.setLevel(level)
              channel.resume()
              channel.prompt(ConsolePromptEvent(state))
          case _ => ()
      case _ => ()

  private[sbt] def currentChannel(state: State): Option[CommandChannel] =
    state.currentCommand match
      case Some(e: Exec) if e.source.isDefined =>
        val source = e.source.get
        StandardMain.exchange.channelForName(source.channelName)
      case _ => None
}

private[sbt] object BackgroundThreadPool {
  sealed trait Status
  case object Waiting extends Status
  final case class Running(thread: Thread) extends Status
  // the oldThread is None if we never ran
  final case class Stopped(oldThread: Option[Thread]) extends Status
}

private[sbt] class BackgroundThreadPool extends java.io.Closeable {

  private val nextThreadId = new java.util.concurrent.atomic.AtomicInteger(1)
  private val threadGroup = Thread.currentThread.getThreadGroup()

  private val threadFactory = new java.util.concurrent.ThreadFactory() {
    override def newThread(runnable: Runnable): Thread = {
      val thread =
        new Thread(threadGroup, runnable, s"sbt-bg-threads-${nextThreadId.getAndIncrement}")
      // Do NOT setDaemon because then the code in TaskExit.scala in sbt will insta-kill
      // the backgrounded process, at least for the case of the run task.
      thread
    }
  }

  private[internal] val executor = new java.util.concurrent.ThreadPoolExecutor(
    0, /* corePoolSize */
    32, /* maxPoolSize, max # of bg tasks */
    2,
    java.util.concurrent.TimeUnit.SECONDS,
    /* keep alive unused threads this long (if corePoolSize < maxPoolSize) */
    new java.util.concurrent.SynchronousQueue[Runnable](),
    threadFactory
  )

  private class BackgroundRunnable(val taskName: String, body: () => Unit)
      extends BackgroundJob
      with Runnable {
    import BackgroundThreadPool._
    private val finishedLatch = new java.util.concurrent.CountDownLatch(1)
    // synchronize to read/write this, no sync to just read
    @volatile
    private var status: Status = Waiting

    // This is used to capture exceptions that are caught in this background job.
    private var exitTry: Option[Try[Unit]] = None

    // double-finally for extra paranoia that we will finishedLatch.countDown
    override def run() =
      try {
        val go = synchronized {
          status match {
            case Waiting =>
              status = Running(Thread.currentThread())
              true
            case Stopped(_) =>
              false
            case Running(_) =>
              throw new RuntimeException("Impossible status of bg thread")
          }
        }
        try {
          if (go) {
            exitTry = Option(Try(body()))
          }
        } finally cleanup()
      } finally finishedLatch.countDown()

    private class StopListener(val callback: () => Unit, val executionContext: ExecutionContext)
        extends Closeable {
      override def close(): Unit = removeListener(this)
      override def hashCode: Int = System.identityHashCode(this)
      override def equals(other: Any): Boolean = other match {
        case r: AnyRef => this eq r
        case _         => false
      }
    }

    // access is synchronized
    private var stopListeners = Set.empty[StopListener]

    private def removeListener(listener: StopListener): Unit = synchronized {
      stopListeners -= listener
    }

    def cleanup(): Unit = {
      // avoid holding any lock while invoking callbacks, and
      // handle callbacks being added by other callbacks, just
      // to be all fancy.
      while (synchronized { stopListeners.nonEmpty }) {
        val listeners = synchronized {
          val list = stopListeners.toList
          stopListeners = Set.empty
          list
        }
        listeners.foreach { l =>
          l.executionContext.execute(() => l.callback())
        }
      }
    }

    override def onStop(listener: () => Unit)(using ex: ExecutionContext): Closeable =
      synchronized {
        val result = new StopListener(listener, ex)
        stopListeners += result
        result
      }
    override def awaitTermination(duration: Duration): Unit = {
      val finished = duration match {
        case fd: FiniteDuration => finishedLatch.await(fd.toMillis, TimeUnit.MILLISECONDS)
        case _                  => finishedLatch.await(); true
      }
      exitTry.foreach(_.fold(e => throw e, identity))
      if (!finished) throw new TimeoutException
    }

    override def humanReadableName: String = taskName
    override def isRunning(): Boolean =
      status match {
        case Waiting               => true // we start as running from BackgroundJob perspective
        case Running(thread)       => thread.isAlive()
        case Stopped(threadOption) => threadOption.map(_.isAlive()).getOrElse(false)
      }
    override def shutdown(): Unit =
      synchronized {
        status match {
          case Waiting =>
            status = Stopped(None) // makes run() not run the body
          case Running(thread) =>
            status = Stopped(Some(thread))
            thread.interrupt()
          case Stopped(threadOption) =>
            // sleep to avoid consuming a lot of CPU
            try {
              Thread.sleep(10)
            } catch {
              case e: InterruptedException =>
                Thread.currentThread().interrupt();
            }
            // try to interrupt again! woot!
            threadOption.foreach(_.interrupt())
        }
      }
  }
  private class BackgroundRunnableWithLoader(
      val loader: Option[ClassLoader],
      taskName: String,
      body: () => Unit
  ) extends BackgroundRunnable(taskName, body) {
    override def awaitTermination(duration: Duration): Unit = {
      try super.awaitTermination(duration)
      finally
        loader.foreach {
          case ac: AutoCloseable   => ac.close()
          case cp: ClasspathFilter => cp.close()
          case _                   =>
        }
    }
  }

  def run(manager: AbstractBackgroundJobService, spawningTask: ScopedKey[?], state: State)(
      work: (Logger, File) => Unit
  ): JobHandle = {
    def start(logger: Logger, workingDir: File): BackgroundJob = {
      val runnable = new BackgroundRunnable(
        spawningTask.key.label,
        { () =>
          work(logger, workingDir)
        }
      )
      executor.execute(runnable)
      runnable
    }
    manager.doRunInBackground(spawningTask, state, start)
  }

  private[sbt] def runWithLoader(
      manager: AbstractBackgroundJobService,
      spawningTask: ScopedKey[?],
      state: State
  )(
      getWork: (Logger, File) => (Option[ClassLoader], () => Unit)
  ): JobHandle = {
    def start(logger: Logger, workingDir: File): BackgroundJob = {
      val (loader, work) = getWork(logger, workingDir)
      val runnable = new BackgroundRunnableWithLoader(loader, spawningTask.key.label, work)
      executor.execute(runnable)
      runnable
    }
    manager.doRunInBackground(spawningTask, state, start)
  }

  override def close(): Unit = {
    executor.shutdown()
  }
}

private[sbt] class DefaultBackgroundJobService(
    private[sbt] val serviceTempDirBase: File,
    override private[sbt] val useLog4J: Boolean
) extends AbstractBackgroundJobService {
  @deprecated("Use the constructor that specifies the background job temporary directory", "1.4.0")
  def this() = this(IO.createTemporaryDirectory, false)
}
private[sbt] object DefaultBackgroundJobService {

  private val backgroundJobServices = new ConcurrentHashMap[File, DefaultBackgroundJobService]
  private[sbt] def shutdown(): Unit = {
    backgroundJobServices.values.forEach(_.shutdown())
    backgroundJobServices.clear()
  }
  private[sbt] lazy val backgroundJobServiceSetting: Setting[?] =
    (GlobalScope / Keys.bgJobService) := {
      val path = (GlobalScope / sbt.Keys.bgJobServiceDirectory).value
      val useLog4J = (GlobalScope / Keys.useLog4J).value
      val newService = new DefaultBackgroundJobService(path, useLog4J)
      backgroundJobServices.putIfAbsent(path, newService) match {
        case null => newService
        case s =>
          newService.shutdown()
          s
      }
    }
  private[sbt] lazy val backgroundJobServiceSettings: Seq[Def.Setting[?]] = Def.settings(
    (GlobalScope / Keys.bgJobServiceDirectory) := {
      sbt.Keys.appConfiguration.value.baseDirectory / "target" / "bg-jobs"
    },
    backgroundJobServiceSetting
  )
}
