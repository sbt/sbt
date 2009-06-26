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

/** This provides functionality to catch System.exit calls to prevent the JVM from terminating.
* This is useful for executing user code that may call System.exit, but actually exiting is
* undesirable.  This file handles the call to exit by disposing all top-level windows and interrupting
* all user started threads.  It does not stop the threads and does not call shutdown hooks.  It is
* therefore inappropriate to use this with code that requires shutdown hooks or creates threads that
* do not terminate.  This category of code should only be called by forking the JVM. */
object TrapExit
{
	/** Executes the given thunk in a context where System.exit(code) throws
	* a custom SecurityException, which is then caught and the exit code returned.
	* Otherwise, 0 is returned.  No other exceptions are handled by this method.*/
	def apply(execute: => Unit, log: Logger): Int =
	{
		log.debug("Starting sandboxed run...")
		
		/** Take a snapshot of the threads that existed before execution in order to determine
		* the threads that were created by 'execute'.*/
		val originalThreads = allThreads
		val code = new ExitCode
		val customThreadGroup = new ExitThreadGroup(new ExitHandler(Thread.getDefaultUncaughtExceptionHandler, originalThreads, code))
		val executionThread = new Thread(customThreadGroup, "run-main") { override def run() { execute } }
		
		val originalSecurityManager = System.getSecurityManager
		try
		{
			val newSecurityManager = new TrapExitSecurityManager(originalSecurityManager, customThreadGroup)
			System.setSecurityManager(newSecurityManager)

			executionThread.start()

			log.debug("Waiting for threads to exit or System.exit to be called.")
			waitForExit(originalThreads, log)
			log.debug("Interrupting remaining threads (should be all daemons).")
			interruptAll(originalThreads) // should only be daemon threads left now
			log.debug("Sandboxed run complete..")
			code.value.getOrElse(0)
		}
		finally { System.setSecurityManager(originalSecurityManager) }
	}
	 // wait for all non-daemon threads to terminate
	private def waitForExit(originalThreads: Set[Thread], log: Logger)
	{
		var daemonsOnly = true
		processThreads(originalThreads, thread =>
			if(!thread.isDaemon)
			{
				daemonsOnly = false
				waitOnThread(thread, log)
			}
		)
		if(!daemonsOnly)
			waitForExit(originalThreads, log)
	}
	/** Waits for the given thread to exit. */
	private def waitOnThread(thread: Thread, log: Logger)
	{
		log.debug("Waiting for thread " + thread.getName + " to exit")
		thread.join
		log.debug("\tThread " + thread.getName + " exited.")
	}
	/** Returns the exit code of the System.exit that caused the given Exception, or rethrows the exception
	* if its cause was not calling System.exit.*/
	private def exitCode(e: Throwable) =
		withCause[TrapExitSecurityException, Int](e)
			{exited => exited.exitCode}
			{other => throw other}
	/** Recurses into the causes of the given exception looking for a cause of type CauseType.  If one is found, `withType` is called with that cause.
	*  If not, `notType` is called with the root cause.*/
	private def withCause[CauseType <: Throwable, T](e: Throwable)(withType: CauseType => T)(notType: Throwable => T)(implicit mf: Manifest[CauseType]): T =
	{
		val clazz = mf.erasure
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
	
	/** Returns all threads that are not in the 'system' thread group and are not the AWT implementation
	* thread (AWT-XAWT, AWT-Windows, ...)*/
	private def allThreads: Set[Thread] =
	{
		val allThreads = wrap.Wrappers.toList(Thread.getAllStackTraces.keySet)
		val threads = new scala.collection.mutable.HashSet[Thread]
		for(thread <- allThreads if !isSystemThread(thread))
			threads += thread
		threads
	}
	/** Returns true if the given thread is in the 'system' thread group and is an AWT thread other than
	* AWT-EventQueue or AWT-Shutdown.*/
	private def isSystemThread(t: Thread) =
	{
		val name = t.getName
		if(name.startsWith("AWT-"))
			!(name.startsWith("AWT-EventQueue") || name.startsWith("AWT-Shutdown"))
		else
		{
			val group = t.getThreadGroup
			(group != null) && (group.getName == "system")
		}
	}
	/** Calls the provided function for each thread in the system as provided by the 
	* allThreads function except those in ignoreThreads.*/
	private def processThreads(ignoreThreads: Set[Thread], process: Thread => Unit)
	{
		allThreads.filter(thread => !ignoreThreads.contains(thread)).foreach(process)
	}
	/** Handles System.exit by disposing all frames and calling interrupt on all user threads */
	private def stopAll(originalThreads: Set[Thread])
	{
		disposeAllFrames()
		interruptAll(originalThreads)
	}
	private def disposeAllFrames()
	{
		val allFrames = java.awt.Frame.getFrames
		if(allFrames.length > 0)
		{
			allFrames.foreach(_.dispose) // dispose all top-level windows, which will cause the AWT-EventQueue-* threads to exit
			Thread.sleep(2000) // AWT Thread doesn't exit immediately, so wait to interrupt it
		}
	}
	// interrupt all threads that appear to have been started by the user
	private def interruptAll(originalThreads: Set[Thread]): Unit =
		processThreads(originalThreads, safeInterrupt)
	// interrupts the given thread, but first replaces the exception handler so that the InterruptedException is not printed
	private def safeInterrupt(thread: Thread)
	{
		if(!thread.getName.startsWith("AWT-"))
		{
			thread.setUncaughtExceptionHandler(new TrapInterrupt(thread.getUncaughtExceptionHandler))
			thread.interrupt
		}
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
	/** An uncaught exception handler that delegates to the original uncaught exception handler except when
	* the cause was a call to System.exit (which generated a SecurityException)*/
	private final class ExitHandler(originalHandler: Thread.UncaughtExceptionHandler, originalThreads: Set[Thread], codeHolder: ExitCode) extends Thread.UncaughtExceptionHandler
	{
		def uncaughtException(t: Thread, e: Throwable)
		{
			try
			{
				codeHolder.set(exitCode(e)) // will rethrow e if it was not because of a call to System.exit
				stopAll(originalThreads)
			}
			catch
			{
				case _ => originalHandler.uncaughtException(t, e)
			}
		}
	}
	private final class ExitThreadGroup(handler: Thread.UncaughtExceptionHandler) extends ThreadGroup("trap.exit")
	{
		override def uncaughtException(t: Thread, e: Throwable) = handler.uncaughtException(t, e)
	}
}
private final class ExitCode extends NotNull
{
	private var code: Option[Int] = None
	def set(c: Int): Unit = synchronized { code = code orElse Some(c) }
	def value: Option[Int] = synchronized { code }
}
///////  These two classes are based on similar classes in Nailgun
/** A custom SecurityManager to disallow System.exit. */
private final class TrapExitSecurityManager(delegateManager: SecurityManager, group: ThreadGroup) extends SecurityManager
{
	import java.security.Permission
	override def checkExit(status: Int)
	{
		val stack = Thread.currentThread.getStackTrace
		if(stack == null || stack.exists(isRealExit))
			throw new TrapExitSecurityException(status)
	}
	/** This ensures that only actual calls to exit are trapped and not just calls to check if exit is allowed.*/
	private def isRealExit(element: StackTraceElement): Boolean =
		element.getClassName == "java.lang.Runtime" && element.getMethodName == "exit"
	override def checkPermission(perm: Permission)
	{
		if(delegateManager != null)
			delegateManager.checkPermission(perm)
	}
	override def checkPermission(perm: Permission, context: AnyRef)
	{
		if(delegateManager != null)
			delegateManager.checkPermission(perm, context)
	}
	override def getThreadGroup = group
}
/** A custom SecurityException that tries not to be caught.*/
private final class TrapExitSecurityException(val exitCode: Int) extends SecurityException
{
	private var accessAllowed = false
	def allowAccess
	{
		accessAllowed = true
	}
	override def printStackTrace = ifAccessAllowed(super.printStackTrace)
	override def toString = ifAccessAllowed(super.toString)
	override def getCause = ifAccessAllowed(super.getCause)
	override def getMessage = ifAccessAllowed(super.getMessage)
	override def fillInStackTrace = ifAccessAllowed(super.fillInStackTrace)
	override def getLocalizedMessage = ifAccessAllowed(super.getLocalizedMessage)
	private def ifAccessAllowed[T](f: => T): T =
	{
		if(accessAllowed)
			f
		else
			throw this
	}
}