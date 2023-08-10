package sbt.util

import java.io.File
import sbt.internal.util.StringVirtualFile1
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
    val x = StringVirtualFile1("a.txt", "")
    val actual = Hasher.hashUnsafe(x)
    assert(actual == blankATxtHash)
  }

  test("tuple") {
    import BasicJsonProtocol.given
    val x = (1, 1)
    val actual = Hasher.hashUnsafe(x)
    assert(actual == 1975280389)
  }
end HasherTest
