package sbt
package internal
package util
package codec

import java.nio.ByteBuffer
import sjsonnew.{ BasicJsonProtocol, BUtil, IsoString }

trait ByteBufferFormats { self: BasicJsonProtocol =>

  /**
   * A string representation of HashedVirtualFileRef, delimited by `>`.
   */
  def byteBufferToStr(buf: ByteBuffer): String =
    BUtil.toHex(buf.array())

  def strToByteBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(BUtil.fromHex(s))

  given byteBufferIsoString: IsoString[ByteBuffer] =
    IsoString.iso(byteBufferToStr, strToByteBuffer)
}
