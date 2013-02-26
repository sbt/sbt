/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
 package xsbt.boot

import java.io.File

// The entry point to the launcher
object Boot
{
	def main(args: Array[String])
	{
		args match {
			case Array("--version") =>
				println("sbt launcher version " + Package.getPackage("xsbt.boot").getImplementationVersion)
			case _ =>
				System.clearProperty("scala.home") // avoid errors from mixing Scala versions in the same JVM
				System.setProperty("jline.shutdownhook", "false")
				CheckProxy()
				initJansi()
				setLogFormat()
				run(args)
		}
	}
	// this arrangement is because Scala does not always properly optimize away
	// the tail recursion in a catch statement
	final def run(args: Array[String]): Unit = runImpl(args) match {
		case Some(newArgs) => run(newArgs)
		case None => ()
	}
	private def runImpl(args: Array[String]): Option[Array[String]] =
		try
			Launch(args.toList) map exit
		catch
		{
			case b: BootException => errorAndExit(b.toString)
			case r: xsbti.RetrieveException => errorAndExit("Error: " + r.getMessage)
			case r: xsbti.FullReload => Some(r.arguments)
			case e: Throwable =>
				e.printStackTrace
				errorAndExit(Pre.prefixError(e.toString))
		}

	private def errorAndExit(msg: String): Nothing =
	{
		System.out.println(msg)
		exit(1)
	}
	private def exit(code: Int): Nothing =
		System.exit(code).asInstanceOf[Nothing]

	private[this] def setLogFormat() {
		if(System.getProperty("sbt.log.format") eq null)
			System.setProperty("sbt.log.format", "true")
	}
	private def initJansi() {
		try {
			val c = Class.forName("org.fusesource.jansi.AnsiConsole")
			c.getMethod("systemInstall").invoke(null)
		} catch {
			case ignore: ClassNotFoundException =>
				/* The below code intentionally traps everything. It technically shouldn't trap the
				* non-StackOverflowError VirtualMachineErrors and AWTError would be weird, but this is PermGen
				* mitigation code that should not render sbt completely unusable if jansi initialization fails.
				* [From Mark Harrah, https://github.com/sbt/sbt/pull/633#issuecomment-11957578].
				*/
			case ex: Throwable => println("Jansi found on class path but initialization failed: " + ex)
		}
	}
}
