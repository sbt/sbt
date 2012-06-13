/* sbt -- Simple Build Tool
 * Copyright 2012 Eugene Vigdorchik */

package sbt

import org.scalacheck._
import Gen._
import Prop._
import java.net.URI

object RichURISpecification extends Properties("Rich URI") {
	val strGen = {
		val charGen = frequency((1, value(' ')), (9, alphaChar))
		val emptyGen = for(cs <- listOf1(charGen)) yield cs.mkString 
		emptyGen map (_.trim) filter (!_.isEmpty)
	}

	def nullable[T >: Null](g: Gen[T]): Gen[T] = frequency((1, value(null)), (25, g))

	implicit def arbitraryURI: Arbitrary[URI] =
		Arbitrary(
			for (scheme <- nullable(identifier);
					 userInfo <- nullable(strGen);
					 host <- identifier;
					 port <- posNum[Int];
					 path <- nullable(strGen);
					 query <- nullable(strGen);
					 fragment <- nullable(strGen))
				 yield {
 					 val pathSlashed = if (path eq null) null else "/" + path
					 new URI(scheme, userInfo, host, port, pathSlashed, query, fragment)
				 }
		)

	property("withoutFragment should drop fragment") = forAll {	(uri: URI) => 
		val uri1 =
			if (uri.getFragment ne null)
				new URI(uri.getScheme, uri.getSchemeSpecificPart, null)
			else
				uri
		new RichURI(uri).withoutFragment == uri1
	}

	property("withoutMarkerScheme should drop scheme") = forAll {	(uri: URI) =>
		val uri1 =
			if (uri.getRawFragment ne null)
				new URI(uri.getRawSchemeSpecificPart + "#" + uri.getRawFragment)
			else
				new URI(uri.getRawSchemeSpecificPart)
		new RichURI(uri).withoutMarkerScheme == uri1
	}
}
