package xsbt

import java.io.File

trait examples
{
	def classpathTask: Task[Set[File]]
	def sourcesTask: Task[Set[File]]
	import DependencyTracking._
	lazy val compile =
		changed(classpathTask, FilesInfo.lastModified, new File("cache/compile/classpath/")) { classpathChanges =>
			changed(sourcesTask, FilesInfo.hash, new File("cache/compile/sources/")) { sourceChanges =>
				invalidate(classpathChanges +++ sourceChanges, new File("cache/compile/dependencies/'")) { (report, tracking) =>
					val recompileSources = report.invalid ** sourceChanges.allInputs
					val classpath = classpathChanges.allInputs
					Task()
				}
			}
		}

	trait sync
	{
		def sources: Task[Set[File]] = Task(Set.empty[File])
		def mapper: Task[FileMapper] = outputDirectory map(FileMapper.basic)
		def outputDirectory: Task[File] = Task(new File("test"))

		import Task._
		lazy val task = syncTask
		def syncTask =
			(sources, mapper) bind { (srcs,mp) =>
				DependencyTracking.trackBasic(sources, FilesInfo.hash, new File("cache/sync/")) { (sourceChanges, report, tracking) =>
					Task
					{
						for(src <- report.invalid ** sourceChanges.allInputs) yield
						{
							val target = mp(src)
							FileUtilities.copyFile(src, target)
							tracking.product(src, target)
							target
						}
					}
				}
			}
	}
}