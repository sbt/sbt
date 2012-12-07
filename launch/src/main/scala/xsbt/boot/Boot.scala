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
				CheckProxy()
				initJansi()
				run(args)
		}
	}
	// this arrangement is because Scala 2.7.7 does not properly optimize away
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
			case e =>
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

	private def initJansi() {
		try {
			val c = Class.forName("org.fusesource.jansi.AnsiConsole")
			c.getMethod("systemInstall").invoke(null)
			if (System.getProperty("sbt.log.format") eq null)
				System.setProperty("sbt.log.format", "true")
		} catch {
			case ignore: ClassNotFoundException =>
			case ex => println("Jansi found on class path but initialization failed: " + ex)
		}
	}
}
