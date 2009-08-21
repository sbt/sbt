/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
 package xsbt.boot

import BootConfiguration.SbtMainClass
import java.io.File

// The entry point to the launcher
object Boot
{
	def main(args: Array[String])
	{
		CheckProxy()
		try { (new Launch(new File("."), SbtMainClass)).boot(args) }
		catch
		{
			case b: BootException => errorAndExit(b)
			case e =>
				e.printStackTrace
				errorAndExit(e)
		}
		System.exit(0)
	}
	private def errorAndExit(e: Throwable)
	{
		System.out.println("Error during sbt execution: " + e.toString)
		System.exit(1)
	}
}
