package xsbt

import java.io.File
import org.specs._
import FileUtilities.{createDirectory, delete, touch, withTemporaryDirectory}
import org.apache.ivy.util.ChecksumHelper

object ComponentManagerTest extends Specification
{
	val TestID = "manager-test"
	"Component manager" should {
		"throw an exception if 'file' is called for a non-existing component" in {
			withManager { _.file(TestID) must throwA[InvalidComponent] }
		}
		"throw an exception if 'file' is called for an empty component" in {
			withManager { manager =>
				createDirectory(manager.location(TestID))
				( manager.file(TestID) ) must throwA[InvalidComponent]
			}
		}
		"return the file for a single-file component" in {
			withManager { manager =>
				createFiles(manager, TestID, "a") match { case Seq(x) =>
					manager.file(TestID).getAbsoluteFile must beEqualTo(x.getAbsoluteFile)
				}
			}
		}

		"throw an exception if 'file' is called for multi-file component" in {
			withManager { manager =>
				createFiles(manager, TestID, "a", "b")
				( manager.file(TestID) ) must throwA[InvalidComponent]
			}
		}
		"return the files for a multi-file component" in {
			withManager { manager =>
				val files = createFiles(manager, TestID, "a", "b")
				manager.files(TestID) must haveTheSameElementsAs(files)
			}
		}
		"return the files for a single-file component" in {
			withManager { manager =>
				val files = createFiles(manager, TestID, "a")
				manager.files(TestID) must haveTheSameElementsAs(files)
			}
		}
		"throw an exception if 'files' is called for a non-existing component" in {
			withManager {   _.files(TestID) must throwA[InvalidComponent] }
		}

		"properly cache a file and then retrieve it to an unresolved component" in {
			withManager { manager =>
				val file = createFile(manager, TestID, "a")
				val hash = checksum(file)
				try
				{
					manager.cache(TestID)
					delete(manager.location(TestID))
					FileUtilities.listFiles(manager.location(TestID)).toList must haveSize(0)
					checksum(manager.file(TestID)) must beEqualTo(hash)
				}
				finally { manager.clearCache(TestID) }
			}
		}

		"not retrieve to a component already resolved" in {
			withManager { manager =>
				val file = createFile(manager, TestID, "a")
				try
				{
					manager.cache(TestID)
					val idDirectory = manager.location(TestID)
					delete(idDirectory)
					createDirectory(idDirectory)
					manager.file(TestID) must throwA[InvalidComponent]
				}
				finally { manager.clearCache(TestID) }
			}
		}
	}
	private def checksum(file: File) = ChecksumHelper.computeAsString(file, "sha1")
	private def createFile(manager: ComponentManager, id: String, name: String): File = createFiles(manager, id, name).toList.head
	private def createFiles(manager: ComponentManager, id: String, names: String*): Seq[File] =
	{
		val dir = manager.location(id)
		createDirectory(dir)
		names.map { name =>
			val testFile = new File(dir, name)
			touch(testFile)
			testFile
		}
	}
	private def withManager[T](f: ComponentManager => T): T =
		TestIvyLogger( logger => withTemporaryDirectory { temp =>  f(new ComponentManager(temp, logger)) } )
}