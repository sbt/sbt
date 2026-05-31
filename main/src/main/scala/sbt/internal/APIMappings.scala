/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.net.{ MalformedURLException, URI }

import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import sbt.librarymanagement.ModuleID
import sbt.internal.util.Attributed
import sbt.util.Logger
import xsbti.HashedVirtualFileRef

private[sbt] object APIMappings {
  def extract(
      cp: Seq[Attributed[HashedVirtualFileRef]],
      log: Logger
  ): Seq[(HashedVirtualFileRef, URI)] =
    cp.flatMap(entry => extractFromEntry(entry, log))

  def extractFromEntry(
      entry: Attributed[HashedVirtualFileRef],
      log: Logger
  ): Option[(HashedVirtualFileRef, URI)] =
    entry.get(Keys.entryApiURL) match
      case Some(u) => Some((entry.data, URI(u)))
      case None    =>
        entry.get(Keys.moduleIDStr).flatMap { str =>
          val mid = Classpaths.moduleIdJsonKeyFormat.read(str)
          extractFromID(entry.data, mid, log)
        }

  private def extractFromID(
      entry: HashedVirtualFileRef,
      mid: ModuleID,
      log: Logger
  ): Option[(HashedVirtualFileRef, URI)] =
    for
      urlString <- mid.extraAttributes.get(SbtPomExtraProperties.POM_API_KEY)
      u <- parseURI(urlString, entry, log)
    yield (entry, u)

  private def parseURI(s: String, forEntry: HashedVirtualFileRef, log: Logger): Option[URI] =
    try Some(new URI(s))
    catch
      case e: MalformedURLException =>
        log.warn(s"Invalid API base URI '$s' for classpath entry '$forEntry': ${e.toString}")
        None

  def store[A](attr: Attributed[A], entryAPI: Option[URI]): Attributed[A] =
    entryAPI match
      case None    => attr
      case Some(u) => attr.put(Keys.entryApiURL, u.toString)
}
