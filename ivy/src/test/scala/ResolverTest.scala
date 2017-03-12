import java.net.URL

import org.specs2.mutable.Specification
import sbt._

object ResolverTest extends Specification {

  "Resolver" should {
    "url" should {
      "propagate pattern descriptorOptional and skipConsistencyCheck." in {
        val pats = Seq("[orgPath]")
        val patsExpected = Seq("http://foo.com/test/[orgPath]")
        val patterns = Resolver
          .url("test", new URL("http://foo.com/test"))(
            Patterns(
              pats,
              pats,
              isMavenCompatible = false,
              descriptorOptional = true,
              skipConsistencyCheck = true
            )
          )
          .patterns

        patterns.ivyPatterns must equalTo(patsExpected)
        patterns.artifactPatterns must equalTo(patsExpected)
        patterns.isMavenCompatible must beFalse
        patterns.skipConsistencyCheck must beTrue
        patterns.descriptorOptional must beTrue
      }
    }
  }

}
