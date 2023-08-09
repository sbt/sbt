package sbt.util

import java.nio.file.{ Files, Path }
import net.openhft.hashing.LongHashFunction
import scala.util.Try

object HashUtil:
  private[sbt] def farmHash(bytes: Array[Byte]): Long =
    LongHashFunction.farmNa().hashBytes(bytes)

  private[sbt] def farmHash(path: Path): Long =
    import sbt.io.Hash
    // allocating many byte arrays for large files may lead to OOME
    // but it is more efficient for small files
    val largeFileLimit = 10 * 1024 * 1024

    if Files.size(path) < largeFileLimit then farmHash(Files.readAllBytes(path))
    else farmHash(Hash(path.toFile))
end HashUtil
