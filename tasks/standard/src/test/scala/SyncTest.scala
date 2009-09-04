package xsbt

import FileUtilities.{read, withTemporaryDirectory => temp, write}

object SyncTest
{
	import Paths._
	def apply(content: String)
	{
		try { test(content) }
		catch { case e: TasksFailed => e.failures.foreach(_.exception.printStackTrace) }
	}
	def test(content: String)
	{
		temp { fromDir => temp { toDir => temp { cacheDir =>
			val from = fromDir / "test"
			val to = toDir / "test-2"
			write(from, content)
			val sync = Sync(cacheDir)( Task( (from, to) :: Nil ))
			val result = TaskRunner(sync.task)
			println(result + "  ::: " +read(to) + "\n\n")
			to.delete
			val result2 = TaskRunner(sync.task)
			println(result2 + "  ::: " +read(to) + "\n\n")
			write(from, content.reverse)
			TaskRunner(sync.clean)
			println(from.exists + " " + fromDir.exists + " " + to.exists + " " + toDir.exists)
		} } }
	}
}
object CompileTest
{
	def apply(dir: String, scalaVersion: String, opts: Seq[String], supers: Set[String])
	{
		def test()
		{
			import Paths._
			import GlobFilter._
			val base = new java.io.File(dir)
			val sources = Task(((base / "src" / "main" / "scala") ** "*.scala") ++ (base * "*.scala"))
			val classpath = Task( dir / "lib" * "*.jar" )
			WithCompiler(scalaVersion) { (compiler, log) =>
				temp { cacheDir => temp { outDir =>
					val options = Task(opts ++ Seq("-d", outDir.getAbsolutePath) )
					val compile = new StandardCompile(sources, classpath, options, Task(supers), Task(compiler), cacheDir, log)
						TaskRunner(compile.task)
						readLine("Press enter to continue...")
						TaskRunner(compile.task)
						readLine("Press enter to continue...")
				} }
			}
		}
		try { test() }
		catch { case e: TasksFailed => e.failures.foreach(_.exception.printStackTrace); case e: Exception => e.printStackTrace }
	}
}