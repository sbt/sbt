package sbt.inc

	import sbt.IO
	import java.io.File
	import collection.mutable

/** During an incremental compilation run, a ClassfileManager deletes class files and is notified of generated class files.
* A ClassfileManager can be used only once.*/
trait ClassfileManager
{
	/** Called once per compilation step with the class files to delete prior to that step's compilation.
	* The files in `classes` must not exist if this method returns normally.
	* Any empty ancestor directories of deleted files must not exist either.*/
	def delete(classes: Iterable[File]): Unit

	/** Called once per compilation step with the class files generated during that step.*/	
	def generated(classes: Iterable[File]): Unit

	/** Called once at the end of the whole compilation run, with `success` indicating whether compilation succeeded (true) or not (false).*/
	def complete(success: Boolean): Unit
}

object ClassfileManager
{
	/** Constructs a minimal ClassfileManager implementation that immediately deletes class files when requested. */
	val deleteImmediately: () => ClassfileManager = () => new ClassfileManager
	{
		def delete(classes: Iterable[File]): Unit = IO.deleteFilesEmptyDirs(classes)
		def generated(classes: Iterable[File]) {}
		def complete(success: Boolean) {}
	}
	/** When compilation fails, this ClassfileManager restores class files to the way they were before compilation.*/
	def transactional(tempDir0: File): () => ClassfileManager = () => new ClassfileManager
	{
		val tempDir = tempDir0.getCanonicalFile
		IO.delete(tempDir)
		IO.createDirectory(tempDir)

		private[this] val generatedClasses = new mutable.HashSet[File]
		private[this] val movedClasses = new mutable.HashMap[File, File]
	
		def delete(classes: Iterable[File])
		{
			for(c <- classes) if(c.exists && !movedClasses.contains(c) && !generatedClasses(c))
				movedClasses.put(c, move(c))
			IO.deleteFilesEmptyDirs(classes)
		}
		def generated(classes: Iterable[File]): Unit = generatedClasses ++= classes
		def complete(success: Boolean)
		{
			if(!success) {
				IO.deleteFilesEmptyDirs(generatedClasses)
				for( (orig, tmp) <- movedClasses ) IO.move(tmp, orig)
			}
			IO.delete(tempDir)
		}

		def move(c: File): File =
		{
			val target = File.createTempFile("sbt", ".class", tempDir)
			IO.move(c, target)
			target
		}
	}
}