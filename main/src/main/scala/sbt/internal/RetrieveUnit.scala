/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.net.URI
import sbt.internal.BuildLoader.ResolveInfo
import sbt.Resolvers.RemoteVcs

object RetrieveUnit {
  def apply(info: ResolveInfo): Option[() => File] = {
    apply(info.uri) match {
      case Some(RemoteVcs.Svn(_)) => Resolvers.subversion(info)
      case Some(RemoteVcs.Hg(_))  => Resolvers.mercurial(info)
      case Some(RemoteVcs.Git(_)) => Resolvers.git(info)
      case _                      =>
        info.uri match {
          case Scheme("http") | Scheme("https") | Scheme("ftp") => Resolvers.remote(info)
          case Scheme("file")                                   => Resolvers.local(info)
          case _                                                => None
        }
    }
  }

  def apply(uri: URI): Option[RemoteVcs] = {
    uri match {
      case Scheme("svn") | Scheme("svn+ssh")   => Some(RemoteVcs.Svn(uri))
      case Scheme("hg")                        => Some(RemoteVcs.Hg(uri))
      case Scheme("git")                       => Some(RemoteVcs.Git(uri))
      case Path(path) if path.endsWith(".git") => Some(RemoteVcs.Git(uri))
      case _                                   => None
    }
  }

  object Scheme {
    def unapply(uri: URI) = Option(uri.getScheme)
  }

  object Path {
    import RichURI.fromURI

    def unapply(uri: URI) = Option(uri.withoutMarkerScheme.getPath)
  }
}
