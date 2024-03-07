package sbt
package internal
package util

import java.io.InputStream
import java.nio.file.{ Files, Path }
import sbt.util.{ Digest, HashUtil }
import xsbti.{ BasicVirtualFileRef, PathBasedFile }

class PlainVirtualFile1(path: Path, id: String) extends BasicVirtualFileRef(id) with PathBasedFile:
  override def contentHash: Long = HashUtil.farmHash(path)
  override def contentHashStr: String = Digest.sha256Hash(input()).toString()
  override def name(): String = path.getFileName.toString
  override def input(): InputStream = Files.newInputStream(path)
  override def toPath: Path = path
end PlainVirtualFile1
