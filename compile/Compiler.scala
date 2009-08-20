package xsbt

import xsbti.{AnalysisCallback, Logger}
import java.io.File
import java.net.URLClassLoader

/** Interface to the Scala compiler.  This class uses the Scala library and compiler obtained through the 'scalaLoader' class
* loader.  This class requires a ComponentManager in order to obtain the interface code to scalac and the analysis plugin.  Because
* these call Scala code for a different Scala version, they must be compiled for the version of Scala being used.
* It is essential that the provided 'scalaVersion' be a 1:1 mapping to the actual version of Scala being used for compilation
* (-SNAPSHOT is not acceptable).  Otherwise, binary compatibility issues will ensue!*/
class Compiler(scalaLoader: ClassLoader, val scalaVersion: String, private[xsbt] val manager: ComponentManager)
{
	// this is the instance used to compile the analysis
	lazy val componentCompiler = new ComponentCompiler(this)
	/** A basic interface to the compiler.  It is called in the same virtual machine, but no dependency analysis is done.  This
	* is used, for example, to compile the interface/plugin code.*/
	object raw
	{
		def apply(arguments: Seq[String])
		{
			// reflection is required for binary compatibility
			 // The following import ensures there is a compile error if the class name changes,
			 //   but it should not be otherwise directly referenced
			import scala.tools.nsc.Main

			val mainClass = Class.forName("scala.tools.nsc.Main", true, scalaLoader)
			val main = mainClass.asInstanceOf[{def process(args: Array[String]): Unit }]
			main.process(arguments.toArray)
		}
	}
	/** Interface to the compiler that uses the dependency analysis plugin.*/
	object analysis
	{
		/** The jar containing the compiled plugin and the compiler interface code.  This will be passed to scalac as a compiler plugin
		* and used to load the class that actually interfaces with Global.*/
		private lazy val interfaceJar = componentCompiler(ComponentCompiler.compilerInterfaceID)
		def apply(arguments: Seq[String], callback: AnalysisCallback, maximumErrors: Int, log: Logger)
		{
			val argsWithPlugin = ("-Xplugin:" + interfaceJar.getAbsolutePath) :: arguments.toList
			val interfaceLoader = new URLClassLoader(Array(interfaceJar.toURI.toURL), scalaLoader)
			val interface = Class.forName("xsbt.CompilerInterface", true, interfaceLoader).newInstance
			val runnable = interface.asInstanceOf[{ def run(args: Array[String], callback: AnalysisCallback, maximumErrors: Int, log: Logger): Unit }]
			 // these arguments are safe to pass across the ClassLoader boundary because the types are defined in Java
			//  so they will be binary compatible across all versions of Scala
			runnable.run(argsWithPlugin.toArray, callback, maximumErrors, log)
		}
		def forceInitialization() {interfaceJar }
	}
}