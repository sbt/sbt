/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 *
 * Partially based on exit trapping in Nailgun by Pete Kirkham,
 * copyright 2004, Martian Software, Inc
 * licensed under Apache 2.0 License.
 */
package sbt

import scala.collection.Set
import scala.reflect.Manifest
import scala.collection.concurrent.TrieMap

import java.lang.ref.WeakReference
import Thread.currentThread
import java.security.Permission
import java.util.concurrent.{ConcurrentHashMap => CMap}
import java.lang.Integer.{toHexString => hex}
import java.lang.Long.{toHexString => hexL}

import TrapExit._

/** Provides an approximation to isolated execution within a single JVM.  
* System.exit calls are trapped to prevent the JVM from terminating.  This is useful for executing
* user code that may call System.exit, but actually exiting is undesirable.
*
* Exit is simulated by disposing all top-level windows and interrupting user-started threads.
* Threads are not stopped and shutdown hooks are not called.  It is
* therefore inappropriate to use this with code that requires shutdown hooks or creates threads that
* do not terminate.  This category of code should only be called by forking a new JVM. */
object TrapExit
{
	/** Run `execute` in a managed context, using `log` for debugging messages.
	* `installManager` must be called before calling this method. */
	def apply(execute: => Unit, log: Logger): Int =
		System.getSecurityManager match {
			case m: TrapExit => m.runManaged(Logger.f0(execute), log)
			case _ => runUnmanaged(execute, log)
		}

	/** Installs the SecurityManager that implements the isolation and returns the previously installed SecurityManager, which may be null.
	* This method must be called before using `apply`. */
	def installManager(): SecurityManager =
		System.getSecurityManager match {
			case m: TrapExit => m
			case m => System.setSecurityManager(new TrapExit(m)); m
		}

	/** Uninstalls the isolation SecurityManager and restores the old security manager. */
	def uninstallManager(previous: SecurityManager): Unit =
		System.setSecurityManager(previous)

	private[this] def runUnmanaged(execute: => Unit, log: Logger): Int =
	{
		log.warn("Managed execution not possible: security manager not installed.")
		try { execute; 0 }
		catch { case e: Exception =>
			log.error("Error during execution: " + e.toString)
			log.trace(e)
			1
		}
	}

	private type ThreadID = String

	/** `true` if the thread `t` is in the TERMINATED state.x*/
	private def isDone(t: Thread): Boolean = t.getState == Thread.State.TERMINATED

	/** Computes an identifier for a Thread that has a high probability of being unique within a single JVM execution. */
	private def computeID(t: Thread): ThreadID =
		// can't use t.getId because when getAccess first sees a Thread, it hasn't been initialized yet
		s"${hex(System.identityHashCode(t))}:${t.getName}"

	/** Waits for the given `thread` to terminate.  However, if the thread state is NEW, this method returns immediately. */
	private def waitOnThread(thread: Thread, log: Logger)
	{
		log.debug("Waiting for thread " + thread.getName + " to terminate.")
		thread.join
		log.debug("\tThread " + thread.getName + " exited.")
	}

	// interrupts the given thread, but first replaces the exception handler so that the InterruptedException is not printed
	private def safeInterrupt(thread: Thread, log: Logger)
	{
		val name = thread.getName
		log.debug("Interrupting thread " + thread.getName)
		thread.setUncaughtExceptionHandler(new TrapInterrupt(thread.getUncaughtExceptionHandler))
		thread.interrupt
		log.debug("\tInterrupted " + thread.getName)
	}
	// an uncaught exception handler that swallows InterruptedExceptions and otherwise defers to originalHandler
	private final class TrapInterrupt(originalHandler: Thread.UncaughtExceptionHandler) extends Thread.UncaughtExceptionHandler
	{
		def uncaughtException(thread: Thread, e: Throwable)
		{
			withCause[InterruptedException, Unit](e)
				{interrupted => ()}
				{other => originalHandler.uncaughtException(thread, e) }
			thread.setUncaughtExceptionHandler(originalHandler)
		}
	}
	/** Recurses into the causes of the given exception looking for a cause of type CauseType.  If one is found, `withType` is called with that cause.
	*  If not, `notType` is called with the root cause.*/
	private def withCause[CauseType <: Throwable, T](e: Throwable)(withType: CauseType => T)(notType: Throwable => T)(implicit mf: Manifest[CauseType]): T =
	{
		val clazz = mf.runtimeClass
		if(clazz.isInstance(e))
			withType(e.asInstanceOf[CauseType])
		else
		{
			val cause = e.getCause
			if(cause == null)
				notType(e)
			else
				withCause(cause)(withType)(notType)(mf)
		}
	}

}

/** Simulates isolation via a SecurityManager.
* Multiple applications are supported by tracking Thread constructions via `checkAccess`.
* The Thread that constructed each Thread is used to map a new Thread to an application.
* This association of Threads with an application allows properly waiting for
* non-daemon threads to terminate or to interrupt the correct threads when terminating.*/
private final class TrapExit(delegateManager: SecurityManager) extends SecurityManager
{
	/** Tracks the number of running applications in order to short-cut SecurityManager checks when no applications are active.*/
	private[this] val running = new java.util.concurrent.atomic.AtomicInteger

	/** Maps a thread to its originating application.  The thread is represented by a unique identifier to avoid leaks. */
	private[this] val threadToApp = new CMap[ThreadID, App]

	/** Executes `f` in a managed context. */
	def runManaged(f: xsbti.F0[Unit], xlog: xsbti.Logger): Int =
	{
		val _ = running.incrementAndGet()
		try runManaged0(f, xlog)
		finally running.decrementAndGet()
	}
	private[this] def runManaged0(f: xsbti.F0[Unit], xlog: xsbti.Logger): Int =
	{
		val log: Logger = xlog
		val app = newApp(f, log)
		val executionThread = new Thread(app, "run-main")
		try {
			executionThread.start() // thread actually evaluating `f`
			finish(app, log)
		}
		catch { case e: InterruptedException => // here, the thread that started the run has been interrupted, not the main thread of the executing code
			cancel(executionThread, app, log)
		}
		finally app.cleanUp()
	}
	/** Interrupt all threads and indicate failure in the exit code. */
	private[this] def cancel(executionThread: Thread, app: App, log: Logger): Int =
	{
		log.warn("Run canceled.")
		executionThread.interrupt()
		stopAllThreads(app)
		1
	}

	/** Wait for all non-daemon threads for `app` to exit, for an exception to be thrown in the main thread,
	* or for `System.exit` to be called in a thread started by `app`. */
	private[this] def finish(app: App, log: Logger): Int =
	{
		log.debug("Waiting for threads to exit or System.exit to be called.")
		waitForExit(app)
		log.debug("Interrupting remaining threads (should be all daemons).")
		stopAllThreads(app) // should only be daemon threads left now
		log.debug("Sandboxed run complete..")
		app.exitCode.value.getOrElse(0)
	}

	// wait for all non-daemon threads to terminate
	private[this] def waitForExit(app: App)
	{
		var daemonsOnly = true
		app.processThreads { thread =>
			// check isAlive because calling `join` on a thread that hasn't started returns immediately
			//  and App will only remove threads that have terminated, which will make this method loop continuously
			//  if a thread is created but not started
			if(thread.isAlive && !thread.isDaemon)
			{
				daemonsOnly = false
				waitOnThread(thread, app.log)
			}
		}
		// processThreads takes a snapshot of the threads at a given moment, so if there were only daemons, the application should shut down
		if(!daemonsOnly)
			waitForExit(app)
	}

	/** Represents an isolated application as simulated by [[TrapExit]].
	* `starterThread` is the user thread that called TrapExit and not the main application thread.
	* `execute` is the application code to evalute.
	* `log` is used for debug logging. */
	private final class App(val starterThread: ThreadID, val execute: xsbti.F0[Unit], val log: Logger) extends Runnable
	{
		val exitCode = new ExitCode
		/** Tracks threads created by this application. To avoid leaks, keys are a unique identifier and values are held via WeakReference.
		* A TrieMap supports the necessary concurrent updates and snapshots.*/
		private[this] val threads = new TrieMap[ThreadID, WeakReference[Thread]]

		def run() {
			try execute()
			catch
			{
				case x: Throwable =>
					exitCode.set(1) //exceptions in the main thread cause the exit code to be 1
					throw x
			}
		}

		/** Records a new thread both in the global [[TrapExit]] manager and for this [[App]].
		* Its uncaught exception handler is configured to log exceptions through `log`. */
		def register(t: Thread, threadID: ThreadID): Unit = if(!isDone(t)) {
			threadToApp.put(threadID, this)
			threads.put(threadID, new WeakReference(t))
			t.setUncaughtExceptionHandler(new LoggingExceptionHandler(log))
		}
		/** Removes a thread from this [[App]] and the global [[TrapExit]] manager. */
		private[this] def unregister(id: ThreadID): Unit = {
			threadToApp.remove(id)
			threads.remove(id)
		}
		/** Final cleanup for this application after it has terminated. */
		def cleanUp(): Unit = {
			val snap = threads.readOnlySnapshot
			threads.clear()
			for( (id, _) <- snap)
				unregister(id)
			threadToApp.remove(starterThread)
		}

		// only want to operate on unterminated threads
		// want to drop terminated threads, including those that have been gc'd
		/** Evaluates `f` on each `Thread` started by this [[App]] at single instant shortly after this method is called. */
		def processThreads(f: Thread => Unit) {
			for((id, tref) <- threads.readOnlySnapshot) {
				val t = tref.get
				if( (t eq null) || isDone(t))
					unregister(id)
				else
					f(t)
				if(isDone(t))
					unregister(id)
			}
		}
	}
	/** Constructs a new application for `f` that will use `log` for debug logging.*/
	private[this] def newApp(f: xsbti.F0[Unit], log: Logger): App = 
	{
		val threadID = computeID(currentThread)
		val a = new App(threadID, f, log)
		threadToApp.put(threadID, a)
		a
	}

	private[this] def stopAllThreads(app: App)
	{
		disposeAllFrames(app)
		interruptAllThreads(app)
	}

	private[this] def interruptAllThreads(app: App): Unit =
		app processThreads { t => if(!isSystemThread(t)) safeInterrupt(t, app.log) else println(s"Not interrupting system thread $t") }

	/** Records a thread if it is not already associated with an application. */
	private[this] def recordThread(t: Thread, threadID: ThreadID)
	{
		val callerID = computeID(Thread.currentThread)
		val app = threadToApp.get(callerID)
		if(app ne null)
			app.register(t, threadID)
	}

	private[this] def getApp(t: Thread): Option[App] =
		Option(threadToApp.get(computeID(t)))

	/** Handles a valid call to `System.exit` by setting the exit code and 
	* interrupting remaining threads for the application associated with `t`, if one exists. */
	private[this] def exitApp(t: Thread, status: Int): Unit = getApp(t) match {
		case None => System.err.println(s"Could not exit($status): no application associated with $t")
		case Some(a) =>
			a.exitCode.set(status)
			stopAllThreads(a)
	}

	/** SecurityManager hook to trap calls to `System.exit` to avoid shutting down the whole JVM.*/
	override def checkExit(status: Int): Unit = if(active) {
		val t = currentThread
		val stack = t.getStackTrace
		if(stack == null || stack.exists(isRealExit)) {
			exitApp(t, status)
			throw new TrapExitSecurityException(status)
		}
	}
	/** This ensures that only actual calls to exit are trapped and not just calls to check if exit is allowed.*/
	private def isRealExit(element: StackTraceElement): Boolean =
		element.getClassName == "java.lang.Runtime" && element.getMethodName == "exit"

	override def checkPermission(perm: Permission)
	{
		if(delegateManager ne null)
			delegateManager.checkPermission(perm)
	}
	override def checkPermission(perm: Permission, context: AnyRef)
	{
		if(delegateManager ne null)
			delegateManager.checkPermission(perm, context)
	}

	/** SecurityManager hook that is abused to record every created Thread and associate it with a managed application. */
	override def checkAccess(t: Thread) {
		if(active) {
			val id = computeID(t)
			if(threadToApp.get(id) eq null)
				recordThread(t, id)
		}
		if(delegateManager ne null)
			delegateManager.checkAccess(t)
	}
	/** `true` if there is at least one application currently being managed. */
	private[this] def active = running.get > 0

	private def disposeAllFrames(app: App) // TODO: allow multiple graphical applications
	{
		val allFrames = java.awt.Frame.getFrames
		if(allFrames.length > 0)
		{
			app.log.debug(s"Disposing ${allFrames.length} top-level windows...")
			allFrames.foreach(_.dispose) // dispose all top-level windows, which will cause the AWT-EventQueue-* threads to exit
			val waitSeconds = 2
			app.log.debug(s"Waiting $waitSeconds s to let AWT thread exit.")
			Thread.sleep(waitSeconds * 1000) // AWT Thread doesn't exit immediately, so wait to interrupt it
		}
	}
	/** Returns true if the given thread is in the 'system' thread group and is an AWT thread other than AWT-EventQueue.*/
	private def isSystemThread(t: Thread) =
	{
		val name = t.getName
		if(name.startsWith("AWT-"))
			!name.startsWith("AWT-EventQueue")
		else
		{
			val group = t.getThreadGroup
			(group != null) && (group.getName == "system")
		}
	}
}

/** A thread-safe, write-once, optional cell for tracking an application's exit code.*/
private final class ExitCode
{
	private var code: Option[Int] = None
	def set(c: Int): Unit = synchronized { code = code orElse Some(c) }
	def value: Option[Int] = synchronized { code }
}

/** The default uncaught exception handler for managed executions.
* It logs the thread and the exception. */
private final class LoggingExceptionHandler(log: Logger) extends Thread.UncaughtExceptionHandler
{
	def uncaughtException(t: Thread, e: Throwable)
	{
		log.error("(" + t.getName + ") " + e.toString)
		log.trace(e)
	}
}
