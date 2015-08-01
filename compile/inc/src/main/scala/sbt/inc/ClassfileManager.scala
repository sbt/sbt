package sbt.inc

import sbt.IO
import java.io.File
import collection.mutable

/**
 * During an incremental compilation run, a ClassfileManager deletes class files and is notified of generated class files.
 * A ClassfileManager can be used only once.
 */
trait ClassfileManager {
  /**
   * Called once per compilation step with the class files to delete prior to that step's compilation.
   * The files in `classes` must not exist if this method returns normally.
   * Any empty ancestor directories of deleted files must not exist either.
   */
  def delete(classes: Iterable[File]): Unit

  /** Called once per compilation step with the class files generated during that step.*/
  def generated(classes: Iterable[File]): Unit

  /** Called once at the end of the whole compilation run, with `success` indicating whether compilation succeeded (true) or not (false).*/
  def complete(success: Boolean): Unit
}

object ClassfileManager {
  /** Constructs a minimal ClassfileManager implementation that immediately deletes class files when requested. */
  val deleteImmediately: () => ClassfileManager = () => new ClassfileManager {
    def delete(classes: Iterable[File]): Unit = IO.deleteFilesEmptyDirs(classes)
    def generated(classes: Iterable[File]) {}
    def complete(success: Boolean) {}
  }
  @deprecated("Use overloaded variant that takes additional logger argument, instead.", "0.13.5")
  def transactional(tempDir0: File): () => ClassfileManager =
    transactional(tempDir0, sbt.Logger.Null)
  /** When compilation fails, this ClassfileManager restores class files to the way they were before compilation.*/
  def transactional(tempDir0: File, logger: sbt.Logger): () => ClassfileManager = () => new ClassfileManager {
    val tempDir = tempDir0.getCanonicalFile
    IO.delete(tempDir)
    IO.createDirectory(tempDir)
    logger.debug(s"Created transactional ClassfileManager with tempDir = $tempDir")

    private[this] val generatedClasses = new mutable.HashSet[File]
    private[this] val movedClasses = new mutable.HashMap[File, File]

    private def showFiles(files: Iterable[File]): String = files.map(f => s"\t$f").mkString("\n")
    def delete(classes: Iterable[File]) {
      logger.debug(s"About to delete class files:\n${showFiles(classes)}")
      val toBeBackedUp = classes.filter(c => c.exists && !movedClasses.contains(c) && !generatedClasses(c))
      logger.debug(s"We backup classs files:\n${showFiles(toBeBackedUp)}")
      for (c <- toBeBackedUp) {
        movedClasses.put(c, move(c))
      }
      IO.deleteFilesEmptyDirs(classes)
    }
    def generated(classes: Iterable[File]): Unit = {
      logger.debug(s"Registering generated classes:\n${showFiles(classes)}")
      generatedClasses ++= classes
    }
    def complete(success: Boolean) {
      if (!success) {
        logger.debug("Rolling back changes to class files.")
        logger.debug(s"Removing generated classes:\n${showFiles(generatedClasses)}")
        IO.deleteFilesEmptyDirs(generatedClasses)
        logger.debug(s"Restoring class files: \n${showFiles(movedClasses.keys)}")
        for ((orig, tmp) <- movedClasses) IO.move(tmp, orig)
      }
      logger.debug(s"Removing the temporary directory used for backing up class files: $tempDir")
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