/* sbt -- Simple Build Tool
 * Copyright 2012 Eugene Vigdorchik */

package sbt

import org.scalacheck._
import Gen._
import Prop._
import java.net.URI
import RichURI._

object RichURISpecification extends Properties("Rich URI") {
  val strGen = {
    val charGen = frequency((1, value(' ')), (9, alphaChar))
    val withEmptyGen = for (cs <- listOf(charGen)) yield cs.mkString
    withEmptyGen map (_.trim.replace(" ", "%20")) filter (!_.isEmpty)
  }

  val pathGen =
    for (s <- listOf1(strGen)) yield s.mkString("/", "/", "")

  def nullable[T >: Null](g: Gen[T]): Gen[T] = frequency((1, value(null)), (25, g))

  implicit val arbitraryURI: Arbitrary[URI] =
    Arbitrary(
      for (
        scheme <- identifier;
        path <- pathGen;
        fragment <- nullable(strGen)
      ) yield new URI(scheme, "file:" + path, fragment)
    )

  property("withoutFragment should drop fragment") = forAll { (uri: URI) =>
    uri.withoutFragment.getFragment eq null
  }

  property("withoutFragment should keep scheme") = forAll { (uri: URI) =>
    uri.withoutFragment.getScheme == uri.getScheme
  }

  property("withoutFragment should keep scheme specific part") = forAll { (uri: URI) =>
    uri.withoutFragment.getSchemeSpecificPart == uri.getSchemeSpecificPart
  }

  property("withoutMarkerScheme should drop marker scheme") = forAll { (uri: URI) =>
    uri.withoutMarkerScheme.getScheme == "file"
  }

  property("withoutMarkerScheme should keep path") = forAll { (uri: URI) =>
    uri.withoutMarkerScheme.getPath == uri.getSchemeSpecificPart.stripPrefix("file:")
  }

  property("withoutMarkerScheme should keep fragment") = forAll { (uri: URI) =>
    uri.withoutMarkerScheme.getFragment == uri.getFragment
  }
}
