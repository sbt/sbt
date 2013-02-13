	import sbt._
	import Keys._
	import Scope.ThisScope

object Sxr
{
	val sxrConf = config("sxr") hide
	val sxr = TaskKey[File]("sxr")
	val sourceDirectories = TaskKey[Seq[File]]("sxr-source-directories")

	lazy val settings: Seq[Setting[_]] = inTask(sxr)(inSxrSettings) ++ baseSettings

	def baseSettings = Seq(
		libraryDependencies += "org.scala-tools.sxr" % "sxr_2.9.0" % "0.2.7" % sxrConf.name
	)
	def inSxrSettings = Seq(
		managedClasspath <<= update map { _.matching( configurationFilter(sxrConf.name) ).classpath },
		scalacOptions <+= sourceDirectories map { "-P:sxr:base-directory:" + _.absString },
		scalacOptions <+= managedClasspath map { "-Xplugin:" + _.files.absString },
		scalacOptions in doc += "-Ymacro-no-expand",
		scaladocOptions <<= scalacOptions,
		target <<= target in taskGlobal apply { _ / "browse" },
		sxr in taskGlobal <<= sxrTask
	)
	def taskGlobal = ThisScope.copy(task = Global)
	def sxrTask = (sources, cacheDirectory, target, scalacOptions in sxr, classpathOptions, scalaInstance, fullClasspath in sxr, streams) map { (srcs, cache, out, opts, cpOpts, si, cp, s) =>
		val outputDir = out.getParentFile / (out.getName + ".sxr")
		val f = FileFunction.cached(cache / "sxr", FilesInfo.hash) { in =>
			s.log.info("Generating sxr output in " + outputDir.getAbsolutePath + "...")
			IO.delete(out)
			IO.createDirectory(out)
			val comp = new compiler.RawCompiler(si, cpOpts, s.log)
			comp(in.toSeq.sorted, cp.files, out, opts)
			Set(outputDir)
		}
		f(srcs.toSet)
		outputDir
	}
}
