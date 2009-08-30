package xsbt

	import java.io.File
	import scala.reflect.Manifest
	import Task._

object Sync
{
	def sources(inputDirectory: Task[File], outputDirectory: Task[File]) =
	{
			import Paths._
		(inputDirectory, outputDirectory) map { (in, out) =>
			FileUtilities.assertDirectories(in, out)
			(in ***) x FileMapper.rebase(in, out)
		}
	}
	def apply(inputDirectory: Task[File], outputDirectory: Task[File], cacheFile: File): Sync =
		apply(sources(inputDirectory, outputDirectory), cacheFile)
	def apply(inputDirectory: Task[File], outputDirectory: Task[File], style: FilesInfo.Style, cacheFile: File): Sync =
		apply(sources(inputDirectory, outputDirectory), style, cacheFile)
	def apply(sources: Task[Iterable[(File,File)]], cacheDirectory: File): Sync =
		apply(sources, FilesInfo.hash, cacheDirectory)
	def apply(sources: Task[Iterable[(File,File)]], style: FilesInfo.Style, cacheDirectory: File): Sync =
		new Sync(sources, style, cacheDirectory)
}
class Sync(val sources: Task[Iterable[(File,File)]], val style: FilesInfo.Style, val cacheDirectory: File) extends TrackedTaskDefinition[Set[File]]
{
	val tracking = new BasicTracked(sources.map(Set() ++ _.map(_._1)), style, cacheFile("sources"))
	val tracked = Seq(tracking)
	
	lazy val task =
		sources bind { srcs =>
			val sourcesTargets = srcs.toSeq
			tracking { (sourceChanges, report, tracking) =>
				Task
				{
					val changed = report.invalid ** sourceChanges.allInputs
					for((source,target) <- sourcesTargets if changed(source))
					{
						FileUtilities.copyFile(source, target)
						tracking.product(source, target)
					}
					Set( sourcesTargets.map(_._2) : _*)
				}
			}
		}
}