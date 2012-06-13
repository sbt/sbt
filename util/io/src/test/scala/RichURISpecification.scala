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

	implicit def arbitraryURI: Arbitrary[URI] =
		Arbitrary(
			for (scheme <- identifier;
					 userInfo <- strGen;
					 host <- identifier;
					 port <- posNum[Int];
					 path <- strGen;
					 query <- strGen;
					 fragment <- strGen)
				 yield new URI(scheme, userInfo, host, port, "/" + path, query, fragment)
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
		val uri1 = new URI(uri.getRawSchemeSpecificPart + "#" + uri.getRawFragment)
		new RichURI(uri).withoutMarkerScheme == uri1
	}
}
