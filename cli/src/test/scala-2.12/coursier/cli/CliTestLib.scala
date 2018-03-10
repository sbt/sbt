package coursier.cli

import coursier.internal.FileUtil
import java.io.{File, FileWriter}


trait CliTestLib {

  def withFile(content: String = "",
               fileName: String = "hello",
               suffix: String = "world")(testCode: (File, FileWriter) => Any) {
    val file = File.createTempFile(fileName, suffix) // create the fixture
    val writer = new FileWriter(file)
    writer.write(content)
    writer.flush()
    try {
      testCode(file, writer) // "loan" the fixture to the test
    } finally {
      writer.close()
      file.delete()
    }
  }

  def withTempDir(
      prefix: String
  )(testCode: File => Any) {
    val dir = FileUtil.createTempDirectory(prefix) // create the fixture
    try {
      testCode(dir) // "loan" the fixture to the test
    } finally {
      cleanDir(dir)
    }
  }

  def cleanDir(tmpDir: File): Unit = {
    def delete(f: File): Boolean =
      if (f.isDirectory) {
        val removedContent =
          Option(f.listFiles()).toSeq.flatten.map(delete).forall(x => x)
        val removedDir = f.delete()

        removedContent && removedDir
      } else
        f.delete()

    if (!delete(tmpDir))
      Console.err.println(
        s"Warning: unable to remove temporary directory $tmpDir")
  }
}
