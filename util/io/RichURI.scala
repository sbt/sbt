/* sbt -- Simple Build Tool
 * Copyright 2011 Sanjin Sehic
 */

package sbt

import java.net.URI

class RichURI(uri: URI)
{
	def hasFragment = uri.getFragment ne null

	def withoutFragment =
		if (hasFragment)
			new URI(uri.getScheme, uri.getSchemeSpecificPart, null)
		else
			uri
}

object RichURI
{
	implicit def fromURI(uri: URI) = new RichURI(uri)
}
