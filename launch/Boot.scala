/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
 package xsbt.boot

import BootConfiguration.{SbtMainClass, SbtModuleName}
import java.io.File

// The entry point to the launcher
object Boot
{
	def main(args: Array[String])
	{
		System.setProperty("scala.home", "") // avoid errors from mixing Scala versions in the same JVM
		CheckProxy()
		try { Launch(args) }
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
