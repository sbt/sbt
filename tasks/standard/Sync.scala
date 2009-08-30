package xsbt

import java.io.File
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
}
class Sync(val sources: Task[Iterable[(File,File)]], val cacheDirectory: File) extends TrackedTaskDefinition[Set[File]]
{
	val tracking = new BasicTracked(sources.map(Set() ++ _.map(_._1)), FilesInfo.hash, cacheFile("sources"))
	val tracked = Seq(tracking)
	
	def this(inputDirectory: Task[File], outputDirectory: Task[File], cacheFile: File) = this(Sync.sources(inputDirectory, outputDirectory), cacheFile)
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