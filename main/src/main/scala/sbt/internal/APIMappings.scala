/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.net.{ MalformedURLException, URI, URL }

import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import sbt.librarymanagement.ModuleID

import sbt.internal.util.Attributed
import sbt.util.Logger
import xsbti.HashedVirtualFileRef

private[sbt] object APIMappings {
  def extract(
      cp: Seq[Attributed[HashedVirtualFileRef]],
      log: Logger
  ): Seq[(HashedVirtualFileRef, URL)] =
    cp.flatMap(entry => extractFromEntry(entry, log))

  def extractFromEntry(
      entry: Attributed[HashedVirtualFileRef],
      log: Logger
  ): Option[(HashedVirtualFileRef, URL)] =
    entry.get(Keys.entryApiURL) match
      case Some(u) => Some((entry.data, URI(u).toURL))
      case None =>
        entry.get(Keys.moduleIDStr).flatMap { str =>
          val mid = Classpaths.moduleIdJsonKeyFormat.read(str)
          extractFromID(entry.data, mid, log)
        }

  private[this] def extractFromID(
      entry: HashedVirtualFileRef,
      mid: ModuleID,
      log: Logger
  ): Option[(HashedVirtualFileRef, URL)] =
    for
      urlString <- mid.extraAttributes.get(SbtPomExtraProperties.POM_API_KEY)
      u <- parseURL(urlString, entry, log)
    yield (entry, u)

  private[this] def parseURL(s: String, forEntry: HashedVirtualFileRef, log: Logger): Option[URL] =
    try Some(new URL(s))
    catch {
      case e: MalformedURLException =>
        log.warn(s"Invalid API base URL '$s' for classpath entry '$forEntry': ${e.toString}")
        None
    }

  def store[A](attr: Attributed[A], entryAPI: Option[URL]): Attributed[A] =
    entryAPI match
      case None    => attr
      case Some(u) => attr.put(Keys.entryApiURL, u.toString)
}
