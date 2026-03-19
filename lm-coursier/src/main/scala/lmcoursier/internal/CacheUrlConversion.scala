/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package lmcoursier.internal

import java.io.File

object CacheUrlConversion {

  final val FileUrlPrefix = "file:"
  final val UnconvertiblePrefix = "${CSR_CACHE}"

  private def normalizePathForComparison(path: String): String =
    path.replace('\\', '/')

  private def normalizedFilePath(fileUrl: String): String = {
    val afterPrefix = fileUrl.stripPrefix(FileUrlPrefix).replaceFirst("^/+", "/")
    val withForwardSlash = normalizePathForComparison(afterPrefix)
    if (
      withForwardSlash.length >= 3 && withForwardSlash
        .charAt(0) == '/' && withForwardSlash.charAt(2) == ':'
    )
      withForwardSlash.substring(1)
    else
      withForwardSlash
  }

  @SuppressWarnings(Array("scalafix:DisableSyntax"))
  def cacheFileToOriginalUrl(fileUrl: String, cacheDir: File): String = {
    if (!fileUrl.startsWith(FileUrlPrefix)) return fileUrl
    val filePath = normalizedFilePath(fileUrl)
    val cachePaths = Seq(
      cacheDir.getAbsolutePath,
      cacheDir.getCanonicalPath
    ).distinct.map(p =>
      normalizePathForComparison(if (p.endsWith("/") || p.endsWith("\\")) p else p + "/")
    )

    def extractHttpUrl(relativePath: String): Option[String] = {
      val protocolSepIndex = relativePath.indexOf('/')
      if (protocolSepIndex > 0) {
        val protocol = relativePath.substring(0, protocolSepIndex)
        val rest = relativePath.substring(protocolSepIndex + 1)
        Some(s"$protocol://$rest")
      } else None
    }

    cachePaths
      .collectFirst {
        case cachePath if filePath.startsWith(cachePath) =>
          val relativePath = filePath.stripPrefix(cachePath)
          extractHttpUrl(relativePath)
      }
      .flatten
      .getOrElse(s"$UnconvertiblePrefix$filePath")
  }

  def isPortableUrl(url: String): Boolean =
    !url.startsWith(FileUrlPrefix) && !url.contains(UnconvertiblePrefix)
}
