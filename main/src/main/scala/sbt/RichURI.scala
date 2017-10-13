/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.net.URI

/** Extends `URI` with additional convenience methods. */
class RichURI(uri: URI) {

  /**
   * Provides a case-class-like `copy` method for URI.
   * Note that this method simply passes the individual components of this URI to the URI constructor
   * that accepts each component individually.  It is thus limited by the implementation restrictions of the relevant methods.
   */
  def copy(scheme: String = uri.getScheme,
           userInfo: String = uri.getUserInfo,
           host: String = uri.getHost,
           port: Int = uri.getPort,
           path: String = uri.getPath,
           query: String = uri.getQuery,
           fragment: String = uri.getFragment) =
    new URI(scheme, userInfo, host, port, path, query, fragment)

  /** Returns `true` if the fragment of the URI is defined. */
  def hasFragment = uri.getFragment ne null

  /** Returns a copy of the URI without the fragment. */
  def withoutFragment =
    if (hasFragment)
      new URI(uri.getScheme, uri.getSchemeSpecificPart, null)
    else
      uri

  /** Returns `true` if the scheme specific part of the URI is also a valid URI. */
  def hasMarkerScheme = new URI(uri.getRawSchemeSpecificPart).getScheme ne null

  /**
   * Strips the wrapper scheme from this URI.
   * If the URI has a fragment, the fragment is transferred to the wrapped URI.
   * If this URI does not have a marker scheme, it is returned unchanged.
   */
  def withoutMarkerScheme = {
    if (hasMarkerScheme)
      if (hasFragment)
        new URI(uri.getRawSchemeSpecificPart + "#" + uri.getRawFragment)
      else
        new URI(uri.getRawSchemeSpecificPart)
    else
      uri
  }
}

object RichURI {

  /** Provides additional convenience methods for `uri`. */
  implicit def fromURI(uri: URI): RichURI = new RichURI(uri)
}
