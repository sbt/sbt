/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.net.URI

/** Extends `URI` with additional convenience methods. */
class RichURI(uri: URI):

  /**
   * Provides a case-class-like `copy` method for URI.
   * Note that this method simply passes the individual components of this URI to the URI constructor
   * that accepts each component individually.  It is thus limited by the implementation restrictions of the relevant methods.
   */
  def copy(
      scheme: String = uri.getScheme, // scruff: ignore(no_default_param)
      userInfo: String = uri.getUserInfo, // scruff: ignore(no_default_param)
      host: String = uri.getHost, // scruff: ignore(no_default_param)
      port: Int = uri.getPort, // scruff: ignore(no_default_param)
      path: String = uri.getPath, // scruff: ignore(no_default_param)
      query: String = uri.getQuery, // scruff: ignore(no_default_param)
      fragment: String = uri.getFragment // scruff: ignore(no_default_param)
  ) =
    new URI(scheme, userInfo, host, port, path, query, fragment)

  /** Returns `true` if the fragment of the URI is defined. */
  def hasFragment = uri.getFragment ne null

  /** Returns a copy of the URI without the fragment. */
  def withoutFragment =
    if hasFragment then new URI(uri.getScheme, uri.getSchemeSpecificPart, null)
    else uri

  /** Returns `true` if the scheme specific part of the URI is also a valid URI. */
  def hasMarkerScheme = new URI(uri.getRawSchemeSpecificPart).getScheme ne null

  /**
   * Strips the wrapper scheme from this URI.
   * If the URI has a fragment, the fragment is transferred to the wrapped URI.
   * If this URI does not have a marker scheme, it is returned unchanged.
   */
  def withoutMarkerScheme =
    if hasMarkerScheme then
      if hasFragment then new URI(uri.getRawSchemeSpecificPart + "#" + uri.getRawFragment)
      else new URI(uri.getRawSchemeSpecificPart)
    else uri
end RichURI

object RichURI:

  /** Provides additional convenience methods for `uri`. */
  implicit def fromURI(uri: URI): RichURI = new RichURI(uri)
