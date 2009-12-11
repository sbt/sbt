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
		try { Launch(args.toList) }
		catch
		{
			case b: BootException => errorAndExit(b.toString)
			case e =>
				e.printStackTrace
				errorAndExit(Pre.prefixError(e.toString))
		}
		System.exit(0)
	}
	private def errorAndExit(msg: String)
	{
		System.out.println(msg)
		System.exit(1)
	}
}
