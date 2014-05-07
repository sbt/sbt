package sbt

import java.io.File
import IO.{ withTemporaryDirectory, write }

object WithFiles {
  /**
   * Takes the relative path -> content pairs and writes the content to a file in a temporary directory.  The written file
   * path is the relative path resolved against the temporary directory path.  The provided function is called with the resolved file paths
   * in the same order as the inputs.
   */
  def apply[T](sources: (File, String)*)(f: Seq[File] => T): T =
    {
      withTemporaryDirectory { dir =>
        val sourceFiles =
          for ((file, content) <- sources) yield {
            assert(!file.isAbsolute)
            val to = new File(dir, file.getPath)
            write(to, content)
            to
          }
        f(sourceFiles)
      }
    }
}