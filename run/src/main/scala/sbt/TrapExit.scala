/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import scala.reflect.Manifest
import scala.collection.concurrent.TrieMap
import java.lang.ref.WeakReference
import Thread.currentThread
import java.security.Permission
import java.util.concurrent.{ ConcurrentHashMap => CMap }
import java.lang.Integer.{ toHexString => hex }
import java.util.function.Supplier

import sbt.util.Logger
import sbt.util.InterfaceUtil
import TrapExit._

/**
 * Provides an approximation to isolated execution within a single JVM.
 * System.exit calls are trapped to prevent the JVM from terminating.  This is useful for executing
 * user code that may call System.exit, but actually exiting is undesirable.
 *
 * Exit is simulated by disposing all top-level windows and interrupting user-started threads.
 * Threads are not stopped and shutdown hooks are not called.  It is
 * therefore inappropriate to use this with code that requires shutdown hooks, creates threads that
 * do not terminate, or if concurrent AWT applications are run.
 * This category of code should only be called by forking a new JVM.
 */
object TrapExit {

  /**
   * Run `execute` in a managed context, using `log` for debugging messages.
   * `installManager` must be called before calling this method.
   */
  def apply(execute: => Unit, log: Logger): Int =
    System.getSecurityManager match {
      case m: TrapExit => m.runManaged(InterfaceUtil.toSupplier(execute), log)
      case _           => runUnmanaged(execute, log)
    }

  /**
   * Installs the SecurityManager that implements the isolation and returns the previously installed SecurityManager, which may be null.
   * This method must be called before using `apply`.
   */
  def installManager(): SecurityManager =
    System.getSecurityManager match {
      case m: TrapExit => m
      case m           => System.setSecurityManager(new TrapExit(m)); m
    }

  /** Uninstalls the isolation SecurityManager and restores the old security manager. */
  def uninstallManager(previous: SecurityManager): Unit =
    System.setSecurityManager(previous)

  private[this] def runUnmanaged(execute: => Unit, log: Logger): Int = {
    log.warn("Managed execution not possible: security manager not installed.")
    try { execute; 0 } catch {
      case e: Exception =>
        log.error("Error during execution: " + e.toString)
        log.trace(e)
        1
    }
  }

  private type ThreadID = String

  /** `true` if the thread `t` is in the TERMINATED state.x*/
  private def isDone(t: Thread): Boolean = t.getState == Thread.State.TERMINATED

  private def computeID(g: ThreadGroup): ThreadID =
    s"g:${hex(System.identityHashCode(g))}:${g.getName}"

  /** Computes an identifier for a Thread that has a high probability of being unique within a single JVM execution. */
  private def computeID(t: Thread): ThreadID =
    // can't use t.getId because when getAccess first sees a Thread, it hasn't been initialized yet
    // can't use t.getName because calling it on AWT thread in certain circumstances generates a segfault (#997):
    //    Apple AWT: +[ThreadUtilities getJNIEnvUncached] attempting to attach current thread after JNFObtainEnv() failed
    s"${hex(System.identityHashCode(t))}"

  /** Waits for the given `thread` to terminate.  However, if the thread state is NEW, this method returns immediately. */
  private def waitOnThread(thread: Thread, log: Logger): Unit = {
    log.debug("Waiting for thread " + thread.getName + " to terminate.")
    thread.join
    log.debug("\tThread " + thread.getName + " exited.")
  }

  // interrupts the given thread, but first replaces the exception handler so that the InterruptedException is not printed
  private def safeInterrupt(thread: Thread, log: Logger): Unit = {
    log.debug("Interrupting thread " + thread.getName)
    thread.setUncaughtExceptionHandler(new TrapInterrupt(thread.getUncaughtExceptionHandler))
    thread.interrupt
    log.debug("\tInterrupted " + thread.getName)
  }
  // an uncaught exception handler that swallows InterruptedExceptions and otherwise defers to originalHandler
  private final class TrapInterrupt(originalHandler: Thread.UncaughtExceptionHandler)
      extends Thread.UncaughtExceptionHandler {
    def uncaughtException(thread: Thread, e: Throwable): Unit = {
      withCause[InterruptedException, Unit](e) { interrupted =>
        ()
      } { other =>
        originalHandler.uncaughtException(thread, e)
      }
      thread.setUncaughtExceptionHandler(originalHandler)
    }
  }

  /**
   * Recurses into the causes of the given exception looking for a cause of type CauseType.  If one is found, `withType` is called with that cause.
   *  If not, `notType` is called with the root cause.
   */
  private def withCause[CauseType <: Throwable, T](e: Throwable)(withType: CauseType => T)(
      notType: Throwable => T)(implicit mf: Manifest[CauseType]): T = {
    val clazz = mf.runtimeClass
    if (clazz.isInstance(e))
      withType(e.asInstanceOf[CauseType])
    else {
      val cause = e.getCause
      if (cause == null)
        notType(e)
      else
        withCause(cause)(withType)(notType)(mf)
    }
  }

}

/**
 * Simulates isolation via a SecurityManager.
 * Multiple applications are supported by tracking Thread constructions via `checkAccess`.
 * The Thread that constructed each Thread is used to map a new Thread to an application.
 * This is not reliable on all jvms, so ThreadGroup creations are also tracked via
 * `checkAccess` and traversed on demand to collect threads.
 * This association of Threads with an application allows properly waiting for
 * non-daemon threads to terminate or to interrupt the correct threads when terminating.
 * It also allows disposing AWT windows if the application created any.
 * Only one AWT application is supported at a time, however.
 */
private final class TrapExit(delegateManager: SecurityManager) extends SecurityManager {

  /** Tracks the number of running applications in order to short-cut SecurityManager checks when no applications are active.*/
  private[this] val running = new java.util.concurrent.atomic.AtomicInteger

  /** Maps a thread or thread group to its originating application.  The thread is represented by a unique identifier to avoid leaks. */
  private[this] val threadToApp = new CMap[ThreadID, App]

  /** Executes `f` in a managed context. */
  def runManaged(f: Supplier[Unit], xlog: xsbti.Logger): Int = {
    val _ = running.incrementAndGet()
    try runManaged0(f, xlog)
    finally running.decrementAndGet()
  }
  private[this] def runManaged0(f: Supplier[Unit], xlog: xsbti.Logger): Int = {
    val log: Logger = xlog
    val app = new App(f, log)
    val executionThread = app.mainThread
    try {
      executionThread.start() // thread actually evaluating `f`
      finish(app, log)
    } catch {
      case _: InterruptedException => // here, the thread that started the run has been interrupted, not the main thread of the executing code
        cancel(executionThread, app, log)
    } finally app.cleanup()
  }

  /** Interrupt all threads and indicate failure in the exit code. */
  private[this] def cancel(executionThread: Thread, app: App, log: Logger): Int = {
    log.warn("Run canceled.")
    executionThread.interrupt()
    stopAllThreads(app)
    1
  }

  /**
   * Wait for all non-daemon threads for `app` to exit, for an exception to be thrown in the main thread,
   * or for `System.exit` to be called in a thread started by `app`.
   */
  private[this] def finish(app: App, log: Logger): Int = {
    log.debug("Waiting for threads to exit or System.exit to be called.")
    waitForExit(app)
    log.debug("Interrupting remaining threads (should be all daemons).")
    stopAllThreads(app) // should only be daemon threads left now
    log.debug("Sandboxed run complete..")
    app.exitCode.value.getOrElse(0)
  }

  // wait for all non-daemon threads to terminate
  private[this] def waitForExit(app: App): Unit = {
    var daemonsOnly = true
    app.processThreads { thread =>
      // check isAlive because calling `join` on a thread that hasn't started returns immediately
      //  and App will only remove threads that have terminated, which will make this method loop continuously
      //  if a thread is created but not started
      if (thread.isAlive && !thread.isDaemon) {
        daemonsOnly = false
        waitOnThread(thread, app.log)
      }
    }
    // processThreads takes a snapshot of the threads at a given moment, so if there were only daemons, the application should shut down
    if (!daemonsOnly)
      waitForExit(app)
  }

  /** Gives managed applications a unique ID to use in the IDs of the main thread and thread group. */
  private[this] val nextAppID = new java.util.concurrent.atomic.AtomicLong
  private def nextID(): String = nextAppID.getAndIncrement.toHexString

  /**
   * Represents an isolated application as simulated by [[TrapExit]].
   * `execute` is the application code to evaluate.
   * `log` is used for debug logging.
   */
  private final class App(val execute: Supplier[Unit], val log: Logger) extends Runnable {

    /**
     * Tracks threads and groups created by this application.
     * To avoid leaks, keys are a unique identifier and values are held via WeakReference.
     * A TrieMap supports the necessary concurrent updates and snapshots.
     */
    private[this] val threads = new TrieMap[ThreadID, WeakReference[Thread]]
    private[this] val groups = new TrieMap[ThreadID, WeakReference[ThreadGroup]]

    /** Tracks whether AWT has ever been used in this jvm execution. */
    @volatile
    var awtUsed = false

    /** The unique ID of the application. */
    val id = nextID()

    /** The ThreadGroup to use to try to track created threads. */
    val mainGroup: ThreadGroup = new ThreadGroup("run-main-group-" + id) {
      private[this] val handler = new LoggingExceptionHandler(log, None)
      override def uncaughtException(t: Thread, e: Throwable): Unit =
        handler.uncaughtException(t, e)
    }
    val mainThread = new Thread(mainGroup, this, "run-main-" + id)

    /** Saves the ids of the creating thread and thread group to avoid tracking them as coming from this application. */
    val creatorThreadID = computeID(currentThread)
    val creatorGroup = currentThread.getThreadGroup

    register(mainThread)
    register(mainGroup)

    val exitCode = new ExitCode

    def run(): Unit = {
      try execute.get()
      catch {
        case x: Throwable =>
          exitCode.set(1) //exceptions in the main thread cause the exit code to be 1
          throw x
      }
    }

    /** Records a new group both in the global [[TrapExit]] manager and for this [[App]].*/
    def register(g: ThreadGroup): Unit =
      if (g != null && g != creatorGroup && !isSystemGroup(g)) {
        val groupID = computeID(g)
        val old = groups.putIfAbsent(groupID, new WeakReference(g))
        if (old.isEmpty) { // wasn't registered
          threadToApp.put(groupID, this)
        }
      }

    /**
     * Records a new thread both in the global [[TrapExit]] manager and for this [[App]].
     * Its uncaught exception handler is configured to log exceptions through `log`.
     */
    def register(t: Thread): Unit = {
      val threadID = computeID(t)
      if (!isDone(t) && threadID != creatorThreadID) {
        val old = threads.putIfAbsent(threadID, new WeakReference(t))
        if (old.isEmpty) { // wasn't registered
          threadToApp.put(threadID, this)
          setExceptionHandler(t)
          if (!awtUsed && isEventQueue(t))
            awtUsed = true
        }
      }
    }

    /** Registers the logging exception handler on `t`, delegating to the existing handler if it isn't the default. */
    private[this] def setExceptionHandler(t: Thread): Unit = {
      val group = t.getThreadGroup
      val previousHandler = t.getUncaughtExceptionHandler match {
        case null | `group` | (_: LoggingExceptionHandler) => None
        case x                                             => Some(x) // delegate to a custom handler only
      }
      t.setUncaughtExceptionHandler(new LoggingExceptionHandler(log, previousHandler))
    }

    /** Removes a thread or group from this [[App]] and the global [[TrapExit]] manager. */
    private[this] def unregister(id: ThreadID): Unit = {
      threadToApp.remove(id)
      threads.remove(id)
      groups.remove(id)
    }

    /** Final cleanup for this application after it has terminated. */
    def cleanup(): Unit = {
      cleanup(threads)
      cleanup(groups)
    }
    private[this] def cleanup(resources: TrieMap[ThreadID, _]): Unit = {
      val snap = resources.readOnlySnapshot
      resources.clear()
      for ((id, _) <- snap)
        unregister(id)
    }

    // only want to operate on unterminated threads
    // want to drop terminated threads, including those that have been gc'd
    /** Evaluates `f` on each `Thread` started by this [[App]] at single instant shortly after this method is called. */
    def processThreads(f: Thread => Unit): Unit = {
      // pulls in threads that weren't recorded by checkAccess(Thread) (which is jvm-dependent)
      //  but can be reached via the Threads in the ThreadGroups recorded by checkAccess(ThreadGroup) (not jvm-dependent)
      addUntrackedThreads()

      val snap = threads.readOnlySnapshot
      for ((id, tref) <- snap) {
        val t = tref.get
        if ((t eq null) || isDone(t))
          unregister(id)
        else {
          f(t)
          if (isDone(t))
            unregister(id)
        }
      }
    }

    // registers Threads from the tracked ThreadGroups
    private[this] def addUntrackedThreads(): Unit =
      groupThreadsSnapshot foreach register

    private[this] def groupThreadsSnapshot: Seq[Thread] = {
      val snap = groups.readOnlySnapshot.values.map(_.get).filter(_ != null)
      threadsInGroups(snap.toList, Nil)
    }

    // takes a snapshot of the threads in `toProcess`, acquiring nested locks on each group to do so
    // the thread groups are accumulated in `accum` and then the threads in each are collected all at
    // once while they are all locked.  This is the closest thing to a snapshot that can be accomplished.
    private[this] def threadsInGroups(toProcess: List[ThreadGroup],
                                      accum: List[ThreadGroup]): List[Thread] = toProcess match {
      case group :: tail =>
        // ThreadGroup implementation synchronizes on its methods, so by synchronizing here, we can workaround its quirks somewhat
        group.synchronized {
          // not tail recursive because of synchronized
          threadsInGroups(threadGroups(group) ::: tail, group :: accum)
        }
      case Nil => accum.flatMap(threads)
    }

    // gets the immediate child ThreadGroups of `group`
    private[this] def threadGroups(group: ThreadGroup): List[ThreadGroup] = {
      val upperBound = group.activeGroupCount
      val groups = new Array[ThreadGroup](upperBound)
      val childrenCount = group.enumerate(groups, false)
      groups.take(childrenCount).toList
    }

    // gets the immediate child Threads of `group`
    private[this] def threads(group: ThreadGroup): List[Thread] = {
      val upperBound = group.activeCount
      val threads = new Array[Thread](upperBound)
      val childrenCount = group.enumerate(threads, false)
      threads.take(childrenCount).toList
    }
  }

  private[this] def stopAllThreads(app: App): Unit = {
    // only try to dispose frames if we think the App used AWT
    // otherwise, we initialize AWT as a side effect of asking for the frames
    // also, we only assume one AWT application at a time
    if (app.awtUsed)
      disposeAllFrames(app.log)
    interruptAllThreads(app)
  }

  private[this] def interruptAllThreads(app: App): Unit =
    app processThreads { t =>
      if (!isSystemThread(t)) safeInterrupt(t, app.log)
      else app.log.debug(s"Not interrupting system thread $t")
    }

  /** Gets the managed application associated with Thread `t` */
  private[this] def getApp(t: Thread): Option[App] =
    Option(threadToApp.get(computeID(t))) orElse getApp(t.getThreadGroup)

  /** Gets the managed application associated with ThreadGroup `group` */
  private[this] def getApp(group: ThreadGroup): Option[App] =
    Option(group).flatMap(g => Option(threadToApp.get(computeID(g))))

  /**
   * Handles a valid call to `System.exit` by setting the exit code and
   * interrupting remaining threads for the application associated with `t`, if one exists.
   */
  private[this] def exitApp(t: Thread, status: Int): Unit = getApp(t) match {
    case None => System.err.println(s"Could not exit($status): no application associated with $t")
    case Some(a) =>
      a.exitCode.set(status)
      stopAllThreads(a)
  }

  /** SecurityManager hook to trap calls to `System.exit` to avoid shutting down the whole JVM.*/
  override def checkExit(status: Int): Unit = if (active) {
    val t = currentThread
    val stack = t.getStackTrace
    if (stack == null || stack.exists(isRealExit)) {
      exitApp(t, status)
      throw new TrapExitSecurityException(status)
    }
  }

  /** This ensures that only actual calls to exit are trapped and not just calls to check if exit is allowed.*/
  private def isRealExit(element: StackTraceElement): Boolean =
    element.getClassName == "java.lang.Runtime" && element.getMethodName == "exit"

  // These are overridden to do nothing because there is a substantial filesystem performance penalty
  // when there is a SecurityManager defined.  The default implementations of these construct a
  // FilePermission, and its initialization involves canonicalization, which is expensive.
  override def checkRead(file: String): Unit = ()
  override def checkRead(file: String, context: AnyRef): Unit = ()
  override def checkWrite(file: String): Unit = ()
  override def checkDelete(file: String): Unit = ()
  override def checkExec(cmd: String): Unit = ()

  override def checkPermission(perm: Permission): Unit = {
    if (delegateManager ne null)
      delegateManager.checkPermission(perm)
  }
  override def checkPermission(perm: Permission, context: AnyRef): Unit = {
    if (delegateManager ne null)
      delegateManager.checkPermission(perm, context)
  }

  /**
   * SecurityManager hook that is abused to record every created Thread and associate it with a managed application.
   * This is not reliably called on different jvm implementations.  On openjdk and similar jvms, the Thread constructor
   * calls setPriority, which triggers this SecurityManager check.  For Java 6 on OSX, this is not called, however.
   */
  override def checkAccess(t: Thread): Unit = {
    if (active) {
      val group = t.getThreadGroup
      noteAccess(group) { app =>
        app.register(group)
        app.register(t)
        app.register(currentThread)
      }
    }
    if (delegateManager ne null)
      delegateManager.checkAccess(t)
  }

  /**
   * This is specified to be called in every Thread's constructor and every time a ThreadGroup is created.
   * This allows us to reliably track every ThreadGroup that is created and map it back to the constructing application.
   */
  override def checkAccess(tg: ThreadGroup): Unit = {
    if (active && !isSystemGroup(tg)) {
      noteAccess(tg) { app =>
        app.register(tg)
        app.register(currentThread)
      }
    }

    if (delegateManager ne null)
      delegateManager.checkAccess(tg)
  }

  private[this] def noteAccess(group: ThreadGroup)(f: App => Unit): Unit =
    getApp(currentThread) orElse getApp(group) foreach f

  private[this] def isSystemGroup(group: ThreadGroup): Boolean =
    (group != null) && (group.getName == "system")

  /** `true` if there is at least one application currently being managed. */
  private[this] def active = running.get > 0

  private def disposeAllFrames(log: Logger): Unit = {
    val allFrames = java.awt.Frame.getFrames
    if (allFrames.nonEmpty) {
      log.debug(s"Disposing ${allFrames.length} top-level windows...")
      allFrames.foreach(_.dispose) // dispose all top-level windows, which will cause the AWT-EventQueue-* threads to exit
      val waitSeconds = 2
      log.debug(s"Waiting $waitSeconds s to let AWT thread exit.")
      Thread.sleep(waitSeconds * 1000L) // AWT Thread doesn't exit immediately, so wait to interrupt it
    }
  }

  /** Returns true if the given thread is in the 'system' thread group or is an AWT thread other than AWT-EventQueue.*/
  private def isSystemThread(t: Thread) =
    if (t.getName.startsWith("AWT-"))
      !isEventQueue(t)
    else
      isSystemGroup(t.getThreadGroup)

  /**
   * An App is identified as using AWT if it gets associated with the event queue thread.
   * The event queue thread is not treated as a system thread.
   */
  private[this] def isEventQueue(t: Thread): Boolean = t.getName.startsWith("AWT-EventQueue")
}

/** A thread-safe, write-once, optional cell for tracking an application's exit code.*/
private final class ExitCode {
  private var code: Option[Int] = None
  def set(c: Int): Unit = synchronized { code = code orElse Some(c) }
  def value: Option[Int] = synchronized { code }
}

/**
 * The default uncaught exception handler for managed executions.
 * It logs the thread and the exception.
 */
private final class LoggingExceptionHandler(log: Logger,
                                            delegate: Option[Thread.UncaughtExceptionHandler])
    extends Thread.UncaughtExceptionHandler {
  def uncaughtException(t: Thread, e: Throwable): Unit = {
    log.error("(" + t.getName + ") " + e.toString)
    log.trace(e)
    delegate.foreach(_.uncaughtException(t, e))
  }
}
