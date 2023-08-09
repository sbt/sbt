package sbt.util

import java.io.{ ByteArrayInputStream, File, InputStream }
import sjsonnew.BasicJsonProtocol
import sjsonnew.support.murmurhash.Hasher
import verify.BasicTestSuite
import xsbti.{ BasicVirtualFileRef, VirtualFile }

object HasherTest extends BasicTestSuite:
  import BasicJsonProtocol.implicitHashWriter

  final val blankContentHash = -7286425919675154353L
  final val blankATxtHash = 950705716L

  test("The IntJsonFormat should convert an Int to an int hash") {
    import BasicJsonProtocol.given
    val actual = Hasher.hashUnsafe[Int](1)
    assert(actual == 1527037976)
  }

  test("StringLong hashing from the implicit scope") {
    import StringLongs.StringLong
    val x = StringLongs.StringLong("a.txt", blankContentHash)
    val actual = Hasher.hashUnsafe(x)
    assert(actual == blankATxtHash)
  }

  test("HashedVirtualFileRef") {
    import PathHashWriters.given
    val x = HashedVirtualFileRef("a.txt", blankContentHash)
    val actual = Hasher.hashUnsafe(x)
    assert(actual == blankATxtHash)
  }

  test("java.io.File hash using farmhash") {
    import PathHashWriters.given
    val x = File("LICENSE")
    val actual = Hasher.hashUnsafe(x)
    assert(actual == 1218007292)
  }

  test("VirtualFile hash") {
    import PathHashWriters.given
    val x = StringVirtualFile("a.txt", "")
    val actual = Hasher.hashUnsafe(x)
    assert(actual == blankATxtHash)
  }

  case class StringVirtualFile(path: String, content: String)
      extends BasicVirtualFileRef(path)
      with VirtualFile:
    override def contentHash: Long = HashUtil.farmHash(content.getBytes("UTF-8"))
    override def input: InputStream = new ByteArrayInputStream(content.getBytes("UTF-8"))
    override def toString: String = s"StringVirtualFile($path, <content>)"
  end StringVirtualFile

  test("tuple") {
    import BasicJsonProtocol.given
    val x = (1, 1)
    val actual = Hasher.hashUnsafe(x)
    assert(actual == 1975280389)
  }
end HasherTest
