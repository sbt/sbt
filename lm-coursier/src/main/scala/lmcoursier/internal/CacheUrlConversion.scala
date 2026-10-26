/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package lmcoursier.internal

import java.io.File
import java.net.URI
import java.nio.file.{ Path, Paths }
import scala.jdk.CollectionConverters.*
import scala.util.Try

object CacheUrlConversion:

  final val FileUrlPrefix = "file:"
  final val UnconvertiblePrefix = "${CSR_CACHE}"

  private def toPath(fileUrl: String): Path =
    Try(Paths.get(new URI(fileUrl))).getOrElse(
      Paths.get(fileUrl.stripPrefix(FileUrlPrefix).replaceFirst("^/+([A-Za-z]:)", "$1"))
    )

  // Coursier caches <protocol>/<user>@<host>/<path> with unsafe characters percent-escaped
  private def toRemoteUrl(relative: Path): Option[String] =
    relative.iterator.asScala.map(_.toString).toVector match
      case protocol +: rest if rest.nonEmpty =>
        for
          escaped <- Try(new URI(s"//${rest.mkString("/")}")).toOption
          authority <- Try(new URI(null, escaped.getAuthority, null, null, null)).toOption
          host <- Option(authority.getHost)
        yield
          val port = if authority.getPort == -1 then "" else s":${authority.getPort}"
          s"$protocol://$host$port${escaped.getPath}"
      case _ => None

  def cacheFileToOriginalUrl(fileUrl: String, cacheDir: File): String =
    if !fileUrl.startsWith(FileUrlPrefix) then fileUrl
    else
      val filePath = toPath(fileUrl).normalize
      Seq(cacheDir.toPath.toAbsolutePath.normalize, cacheDir.getCanonicalFile.toPath).distinct
        .collectFirst {
          case cachePath if filePath.startsWith(cachePath) => cachePath.relativize(filePath)
        }
        .flatMap(toRemoteUrl)
        .getOrElse(s"$UnconvertiblePrefix${filePath.toString.replace('\\', '/')}")

  def isPortableUrl(url: String): Boolean =
    !url.startsWith(FileUrlPrefix) && !url.contains(UnconvertiblePrefix)
end CacheUrlConversion
