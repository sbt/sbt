/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
 package xsbt.boot

import java.io.File

// The entry point to the launcher
object Boot
{
	def main(args: Array[String])
	{
		System.clearProperty("scala.home") // avoid errors from mixing Scala versions in the same JVM
		CheckProxy()
		run(args)
	}
	// this arrangement is because Scala 2.7.7 does not properly optimize away
	// the tail recursion in a catch statement
	final def run(args: Array[String]): Unit = run(runImpl(args))
	private def runImpl(args: Array[String]): Array[String] =
	{
		try
		{
			Launch(args.toList)
			System.exit(0).asInstanceOf[Nothing]
		}
		catch
		{
			case b: BootException => errorAndExit(b.toString)
			case r: xsbti.RetrieveException =>errorAndExit("Error: " + r.getMessage) 
			case r: xsbti.FullReload => r.arguments
			case e =>
				e.printStackTrace
				errorAndExit(Pre.prefixError(e.toString))
		}
	}
	private def errorAndExit(msg: String): Nothing =
	{
		System.out.println(msg)
		System.exit(1).asInstanceOf[Nothing]
	}
}
