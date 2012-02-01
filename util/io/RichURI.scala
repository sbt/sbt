/* sbt -- Simple Build Tool
 * Copyright 2011 Sanjin Sehic
 */

package sbt

import java.net.URI

class RichURI(uri: URI)
{
	def copy(scheme: String = uri.getScheme, userInfo: String = uri.getUserInfo,
					 host: String = uri.getHost, port: Int = uri.getPort, path: String = uri.getPath,
					 query: String = uri.getQuery, fragment: String = uri.getFragment) =
		new URI(scheme, userInfo, host, port, path, query, fragment)

	def hasFragment = uri.getFragment ne null

	def withoutFragment =
		if (hasFragment)
			new URI(uri.getScheme, uri.getSchemeSpecificPart, null)
		else
			uri

	def hasMarkerScheme = new URI(uri.getSchemeSpecificPart).getScheme ne null

	def withoutMarkerScheme =
	{
		if (hasMarkerScheme)
			if (hasFragment)
				new URI(uri.getSchemeSpecificPart + "#" + uri.getFragment)
			else
				new URI(uri.getSchemeSpecificPart)
		else
			uri
	}
}

object RichURI
{
	implicit def fromURI(uri: URI) = new RichURI(uri)
}
