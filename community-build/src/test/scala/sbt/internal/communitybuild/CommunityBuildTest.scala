package sbt
package internal
package communitybuild

import org.junit.Test
import org.junit.Assert.fail
import org.junit.experimental.categories.Category

import CommunityBuildRunner.run

class TestCategory

given testRunner: CommunityBuildRunner with
  override def failWith(msg: String) = { fail(msg); ??? }

@Category(Array(classOf[TestCategory]))
class CommunityBuildTestA:
  @Test def parboiled2 = projects.parboiled2.run()
  @Test def `sbt-compile-benchmark` = projects.`sbt-compile-benchmark`.run()
  @Test def scalaz = projects.scalaz.run()
end CommunityBuildTestA
