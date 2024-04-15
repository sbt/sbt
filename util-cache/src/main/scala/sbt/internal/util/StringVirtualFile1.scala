package sbt.internal.util

import java.io.{ ByteArrayInputStream, InputStream }
import sbt.util.{ Digest, HashUtil }
import xsbti.{ BasicVirtualFileRef, VirtualFile }

case class StringVirtualFile1(path: String, content: String)
    extends BasicVirtualFileRef(path)
    with VirtualFile:
  override def contentHash: Long = HashUtil.farmHash(content.getBytes("UTF-8"))
  override def sizeBytes: Long = content.getBytes("UTF-8").size
  override def contentHashStr: String =
    import Digest.*
    val d = Digest.sha256Hash(content.getBytes("UTF-8"))
    d.contentHashStr
  override def input: InputStream = new ByteArrayInputStream(content.getBytes("UTF-8"))
  override def toString: String = s"StringVirtualFile1($path, <content>)"
end StringVirtualFile1
