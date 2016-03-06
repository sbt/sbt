package coursier
package test

import java.io.File
import java.math.BigInteger
import java.util.concurrent.Executors

import utest._

import scalaz.concurrent.Strategy


object ChecksumTests extends TestSuite {
  val tests = TestSuite {

    'parse - {
      // https://repo1.maven.org/maven2/org/apache/spark/spark-core_2.11/1.2.0/spark-core_2.11-1.2.0.pom.sha1
      // as of 2016-03-02
      val junkSha1 =
        "./spark-core_2.11/1.2.0/spark-core_2.11-1.2.0.pom:\n" +
          "5630 42A5 4B97 E31A F452  9EA0 DB79 BA2C 4C2B B6CC"

      val cleanSha1 = "563042a54b97e31af4529ea0db79ba2c4c2bb6cc"

      val checksum = Some(new BigInteger(cleanSha1, 16))

      'junk - {
        assert(Cache.parseChecksum(junkSha1) == checksum)
      }

      'clean - {
        assert(Cache.parseChecksum(cleanSha1) == checksum)
      }
    }

    'artifact - {

      val cachePath = getClass.getResource("/test-repo").getPath

      val cache = new File(cachePath)

      def validate(artifact: Artifact, sumType: String) =
        Cache.validateChecksum(
          artifact,
          sumType,
          cache,
          Strategy.DefaultExecutorService
        ).run.run

      def artifact(url: String) = Artifact(
        url,
        Map(
          "MD5" -> (url + ".md5"),
          "SHA-1" -> (url + ".sha1")
        ),
        Map.empty,
        Attributes("jar"),
        changing = false
      )

      val artifacts = Seq(
        "http://abc.com/com/abc/test/0.1/test-0.1.pom",
        // corresponding SHA-1 starts with a 0
        "http://abc.com/com/github/alexarchambault/coursier_2.11/1.0.0-M9/coursier_2.11-1.0.0-M9.pom"
      ).map(artifact)

      def validateAll(sumType: String) =
        for (artifact <- artifacts) {
          val res = validate(artifact, sumType)
          assert(res.isRight)
        }

      'sha1 - validateAll("SHA-1")
      'md5  - validateAll("MD5")
    }
  }
}