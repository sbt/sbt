package sbt.internal.librarymanagement

import java.io.File
import java.util.concurrent.Callable
import sbt.io.IO.withTemporaryDirectory
import sbt.io.IO
import org.apache.ivy.util.ChecksumHelper
import IfMissing.Fail
import xsbti.ComponentProvider
import verify.BasicTestSuite

// TODO - We need to re-enable this test.  Right now, we dont' have a "stub" launcher for this.
//        This is testing something which uses a launcher interface, but was grabbing the underlying class directly
//        when it really should, instead, be stubbing out the underyling class.

object ComponentManagerTest extends BasicTestSuite {
  val TestID = "manager-test"

  /*
  test(
    "Component manager should throw an exception if 'file' is called for a non-existing component"
  ) {
    withManager { manager =>
      intercept[InvalidComponent] {
        manager.file(TestID)(Fail)
        ()
      }
    }
  }

  test("it should throw an exception if 'file' is called for an empty component") {
    withManager { manager =>
      manager.define(TestID, Nil)
      intercept[InvalidComponent] {
        manager.file(TestID)(Fail)
        ()
      }
    }
  }

  test("it should return the file for a single-file component") {
    withManager { manager =>
      val hash = defineFile(manager, TestID, "a")
      assert(checksum(manager.file(TestID)(Fail)) == hash)
    }
  }

  test("it should throw an exception if 'file' is called for multi-file component") {
    withManager { manager =>
      defineFiles(manager, TestID, "a", "b")
      intercept[InvalidComponent] {
        manager.file(TestID)(Fail)
        ()
      }
    }
  }

  test("it should return the files for a multi-file component") {
    withManager { manager =>
      val hashes = defineFiles(manager, TestID, "a", "b")
      assert(checksum(manager.files(TestID)(Fail)).toSet == hashes.toSet)
    }
  }

  test("it should return the files for a single-file component") {
    withManager { manager =>
      val hashes = defineFiles(manager, TestID, "a")
      assert(checksum(manager.files(TestID)(Fail)).toSet == hashes.toSet)
    }
  }

  test("it should throw an exception if 'files' is called for a non-existing component") {
    withManager { manager =>
      intercept[InvalidComponent] {
        manager.files(TestID)(Fail)
        ()
      }
    }
  }

  test("it should properly cache a file and then retrieve it to an unresolved component") {
    withTemporaryDirectory { ivyHome =>
      withManagerHome(ivyHome) { definingManager =>
        val hash = defineFile(definingManager, TestID, "a")
        try {
          definingManager.cache(TestID)
          withManagerHome(ivyHome) { usingManager =>
            assert(checksum(usingManager.file(TestID)(Fail)) == hash)
          }
        } finally {
          definingManager.clearCache(TestID)
        }
      }
    }
  }

  private def checksum(files: Iterable[File]): Seq[String] = files.map(checksum).toSeq
  private def checksum(file: File): String =
    if (file.exists) ChecksumHelper.computeAsString(file, "sha1") else ""
  private def defineFile(manager: ComponentManager, id: String, name: String): String =
    createFile(manager, id, name)(checksum)
  private def defineFiles(manager: ComponentManager, id: String, names: String*): Seq[String] =
    createFiles(manager, id, names: _*)(checksum)
  private def createFile[T](manager: ComponentManager, id: String, name: String)(f: File => T): T =
    createFiles(manager, id, name)(files => f(files.toList.head))
  private def createFiles[T](manager: ComponentManager, id: String, names: String*)(
      f: Seq[File] => T
  ): T =
    withTemporaryDirectory { dir =>
      val files = names.map(name => new File(dir, name))
      files.foreach(writeRandomContent)
      manager.define(id, files)
      f(files)
    }
  private def writeRandomContent(file: File) = IO.write(file, randomString)
  private def randomString = "asdf"
  private def withManager[T](f: ComponentManager => T): T =
    withTemporaryDirectory { ivyHome =>
      withManagerHome(ivyHome)(f)
    }

  private def withManagerHome[T](ivyHome: File)(f: ComponentManager => T): T =
    TestLogger { logger =>
      withTemporaryDirectory { temp =>
        // The actual classes we'll use at runtime.
        // val mgr = new ComponentManager(xsbt.boot.Locks, new xsbt.boot.ComponentProvider(temp, true), Some(ivyHome), logger)

        // A stub component manager
        object provider extends ComponentProvider {
          override def componentLocation(id: String): File = new File(temp, id)
          override def lockFile(): File = {
            IO.createDirectory(temp)
            new java.io.File(temp, "sbt.components.lock")
          }
          override def defineComponent(id: String, files: Array[File]): Unit = {
            val location = componentLocation(id)
            if (location.exists)
              throw new RuntimeException(
                s"Cannot redefine component.  ID: $id, files: ${files.mkString(",")}"
              )
            else {
              IO.copy(files.map { f =>
                f -> new java.io.File(location, f.getName)
              })
              ()
            }
          }
          override def addToComponent(id: String, files: Array[File]): Boolean = {
            val location = componentLocation(id)
            IO.copy(files.map { f =>
              f -> new java.io.File(location, f.getName)
            })
            true
          }
          override def component(id: String): Array[File] =
            Option(componentLocation(id).listFiles())
              .map(_.filter(_.isFile))
              .getOrElse(Array.empty)
        }
        // A stubbed locking API.
        object locks extends xsbti.GlobalLock {
          override def apply[U](lockFile: File, run: Callable[U]): U = {
            // TODO - do we need to lock?
            run.call()
          }
        }
        val mgr = new ComponentManager(locks, provider, Some(ivyHome), logger)
        f(mgr)
      }
    }
   */
}
