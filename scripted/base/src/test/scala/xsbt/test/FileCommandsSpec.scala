package xsbt.test

import java.io.File
import org.specs2.mutable.Specification
import org.specs2.matcher.FileMatchers
import sbt._
import sbt.Path._

object FileCommandsSpec extends Specification with FileMatchers {

  "The touch command" should {
    "touch a file that doesn't exist" in withTmpDir { dir =>
      fileCommands(dir)("touch", List("foo"))
      dir / "foo" must exist
    }
    "update the timestamp of a file that does exist" in withTmpDir { dir =>
      val file = dir / "foo"
      IO.write(file, "x")
      file.setLastModified(1000L)

      fileCommands(dir)("touch", List("foo"))
      file.lastModified() must beGreaterThan(1000L)
    }
  }

  "The delete command" should {
    "delete a file" in withTmpDir { dir =>
      IO.write(dir / "foo", "x")
      fileCommands(dir)("delete", List("foo"))
      dir / "foo" must not(exist)
    }
    "delete a directory" in withTmpDir { dir =>
      IO.write(dir / "foo" / "bar", "x")
      fileCommands(dir)("delete", List("foo"))
      dir / "foo" must not(exist)
    }
  }

  "The exists command" should {
    "succeed if a file exists" in withTmpDir { dir =>
      IO.write(dir / "foo", "x")
      fileCommands(dir)("exists", List("foo"))
      ok
    }
    "fail if a file doesn't exist" in withTmpDir { dir =>
      fileCommands(dir)("exists", List("foo")) must throwAn[Exception]
    }
  }

  "The mkdir command" should {
    "make a directory" in withTmpDir { dir =>
      fileCommands(dir)("mkdir", List("foo"))
      dir / "foo" must beADirectory
    }
    "make all directories" in withTmpDir { dir =>
      fileCommands(dir)("mkdir", List("foo/bar"))
      dir / "foo" / "bar" must beADirectory
    }
  }

  "The absent command" should {
    "succeed if a file is absent" in withTmpDir { dir =>
      fileCommands(dir)("absent", List("foo"))
      ok
    }
    "fail if a file is not absent" in withTmpDir { dir =>
      IO.write(dir / "foo", "x")
      fileCommands(dir)("absent", List("foo")) must throwAn[Exception]
    }
  }

  "The newer command" should {
    "succeed if a file is newer" in withTmpDir { dir =>
      val file1 = dir / "foo"
      IO.write(file1, "x")
      file1.setLastModified(1000L)
      val file2 = dir / "bar"
      IO.write(file2, "x")
      file2.setLastModified(2000L)

      fileCommands(dir)("newer", List("bar", "foo"))
      ok
    }
    "fail if a file is not newer" in withTmpDir { dir =>
      val file1 = dir / "foo"
      IO.write(file1, "x")
      file1.setLastModified(1000L)
      val file2 = dir / "bar"
      IO.write(file2, "x")
      file2.setLastModified(2000L)

      fileCommands(dir)("newer", List("foo", "bar")) must throwAn[Exception]
    }
    "fail if the tested file doesn't exist" in withTmpDir { dir =>
      val file1 = dir / "foo"
      IO.write(file1, "x")
      file1.setLastModified(1000L)

      fileCommands(dir)("newer", List("bar", "foo")) must throwAn[Exception]
    }
    "succeed if the target file doesn't exist" in withTmpDir { dir =>
      val file1 = dir / "foo"
      IO.write(file1, "x")
      file1.setLastModified(1000L)

      fileCommands(dir)("newer", List("foo", "bar"))
      ok
    }
  }

  "The copy-file command" should {
    "copy a file" in withTmpDir { dir =>
      IO.write(dir / "foo", "x")
      fileCommands(dir)("copy-file", List("foo", "bar"))
      dir / "bar" must exist
      IO.read(dir / "bar") must_== "x"

    }
  }

  def fileCommands(dir: File) = new FileCommands(dir)

  def withTmpDir[A](block: File => A): A = {
    val tmpDir = File.createTempFile("filecommands", "")
    try {
      tmpDir.delete()
      tmpDir.mkdir()
      block(tmpDir)
    } finally {
      IO.delete(tmpDir)
    }

  }
}
