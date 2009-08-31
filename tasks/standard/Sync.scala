package xsbt

	import java.io.File
	import scala.reflect.Manifest
	import Task._

object SyncDirs
{
	def sources(inputDirectory: Task[File], outputDirectory: Task[File]) =
	{
			import Paths._
		(inputDirectory, outputDirectory) map { (in, out) =>
			FileUtilities.assertDirectories(in, out)
			(in ***) x FileMapper.rebase(in, out)
		}
	}
	def apply(cacheDirectory: File)(inputDirectory: Task[File], outputDirectory: Task[File]): Sync =
		Sync(cacheDirectory)(sources(inputDirectory, outputDirectory))
	def apply(cacheDirectory: File, inputStyle: FilesInfo.Style, outputStyle: FilesInfo.Style)(inputDirectory: Task[File], outputDirectory: Task[File]): Sync =
		Sync(cacheDirectory, inputStyle, outputStyle)(sources(inputDirectory, outputDirectory))
}
object Sync
{
	def apply(cacheDirectory: File)(sources: Task[Iterable[(File,File)]]): Sync =
		apply(cacheDirectory, FilesInfo.hash, FilesInfo.lastModified)(sources)
	def apply(cacheDirectory: File, inputStyle: FilesInfo.Style, outputStyle: FilesInfo.Style)(sources: Task[Iterable[(File,File)]]): Sync =
		new Sync(cacheDirectory, inputStyle, outputStyle)(sources)
}
class Sync(val cacheDirectory: File, val inputStyle: FilesInfo.Style, val outputStyle: FilesInfo.Style)(val sources: Task[Iterable[(File,File)]]) extends TrackedTaskDefinition[Set[File]]
{
	private val invalidation = InvalidateFiles(cacheDirectory)
	private val changedInputs = Difference.inputs(extract(_._1), inputStyle, cacheFile("inputs"))
	private val changedOutputs = Difference.outputs(extract(_._2), outputStyle, cacheFile("outputs"))
	private def extract(f: ((File,File)) => File) = sources.map(Set() ++ _.map(f))
	
	val tracked = Seq(changedOutputs, changedInputs, invalidation)
	
	lazy val task =
		sources bind { srcs =>
			val sourcesTargets = srcs.toSeq
			changedInputs { inputChanges =>
				changedOutputs { outputChanges =>
					invalidation(inputChanges +++ outputChanges) { (report, tracking) =>
						Task
						{
							val invalidInputs = report.invalid ** inputChanges.checked
							val invalidOutputs = report.invalid ** outputChanges.checked
							for((source,target) <- sourcesTargets if invalidInputs(source) ||invalidOutputs(target) )
							{
								FileUtilities.copyFile(source, target)
								tracking.product(source, target)
							}
							Set( outputChanges.checked.toSeq : _*)
						}
					}
				}
			}
		}
}