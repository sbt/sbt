package sbt
package compiler

	import xsbt.boot
	import java.io.File
	import IO.withTemporaryDirectory

object WithCompiler
{
	def apply[T](scalaVersion: String)(f: (AnalyzingCompiler, Logger) => T): T =
	{
		launcher { (launch, log) =>
			withTemporaryDirectory { componentDirectory =>
				val manager = new ComponentManager(xsbt.boot.Locks, new boot.ComponentProvider(componentDirectory), log)
				val compiler = new AnalyzingCompiler(ScalaInstance(scalaVersion, launch), manager, log)
				compiler.newComponentCompiler(log).clearCache(ComponentCompiler.compilerInterfaceID)
				define(manager, ComponentCompiler.compilerInterfaceSrcID, getResource("CompilerInterface.scala"), getClassResource(classOf[jline.Completor]))
				define(manager, ComponentCompiler.xsbtiID, getClassResource(classOf[xsbti.AnalysisCallback]))
				f(compiler, log)
			}
		}
	}
	def launcher[T](f: (xsbti.Launcher, Logger) => T): T =
		TestLogger { log => 
			boot.LaunchTest.withLauncher { launch => f(launch, log) }
		}

	def getClassResource(resource: Class[_]): File = IO.classLocationFile(resource)
	def getResource(resource: String): File = 
	{
		val src = getClass.getClassLoader.getResource(resource)
		if(src ne null) IO.asFile(src) else error("Resource not found: " + resource)
	}
	def define(manager: ComponentManager, id: String, files: File*)
	{
		manager.clearCache(id)
		manager.define(id, files)
	}
}