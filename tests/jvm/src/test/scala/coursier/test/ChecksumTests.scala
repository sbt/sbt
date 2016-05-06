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

      def sha1ParseTest(clean: String, others: String*): Unit = {
        val expected = Some(new BigInteger(clean, 16))

        assert(Cache.parseChecksum(clean) == expected)
        for (other <- others)
          assert(Cache.parseChecksum(other) == expected)
      }

      * - {
        // https://repo1.maven.org/maven2/org/apache/spark/spark-core_2.11/1.2.0/spark-core_2.11-1.2.0.pom.sha1
        // as of 2016-03-02
        val junkSha1 =
          "./spark-core_2.11/1.2.0/spark-core_2.11-1.2.0.pom:\n" +
          "5630 42A5 4B97 E31A F452  9EA0 DB79 BA2C 4C2B B6CC"

        val cleanSha1 = "563042a54b97e31af4529ea0db79ba2c4c2bb6cc"

        sha1ParseTest(cleanSha1, junkSha1)
      }

      * - {
        // https://repo1.maven.org/maven2/org/json/json/20080701/json-20080701.pom.sha1
        // as of 2016-03-05
        val dirtySha1 =
          "4bf5daa95eb5c12d753a359a3e00621fdc73d187  " + // no CR here
          "/home/maven/repository-staging/to-ibiblio/maven2/org/json/json/20080701/json-20080701.pom"

        val cleanSha1 = "4bf5daa95eb5c12d753a359a3e00621fdc73d187"

        sha1ParseTest(cleanSha1, dirtySha1)
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
        changing = false,
        authentication = None
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