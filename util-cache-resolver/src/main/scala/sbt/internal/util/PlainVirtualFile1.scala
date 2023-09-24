package sbt
package internal
package util

import java.io.InputStream
import java.nio.file.{ Files, Path, Paths }
import sbt.util.HashUtil
import xsbti.{ BasicVirtualFileRef, FileConverter, PathBasedFile, VirtualFileRef, VirtualFile }

class PlainVirtualFile1(path: Path, id: String) extends BasicVirtualFileRef(id) with PathBasedFile:
  override def contentHash: Long = HashUtil.farmHash(path)
  override def contentHashStr: String = HashUtil.toFarmHashString(contentHash)
  override def name(): String = path.getFileName.toString
  override def input(): InputStream = Files.newInputStream(path)
  override def toPath: Path = path
end PlainVirtualFile1
