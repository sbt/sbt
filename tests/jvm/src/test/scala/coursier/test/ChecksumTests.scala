package coursier
package test

import java.io.File
import java.util.concurrent.Executors

import utest._


object ChecksumTests extends TestSuite {
  val tests = TestSuite {

    val junkSha1 =
      """./spark-core_2.11/1.2.0/spark-core_2.11-1.2.0.pom:
5630 42A5 4B97 E31A F452  9EA0 DB79 BA2C 4C2B B6CC
      """.stripMargin

    val cleanSha1 =
      """563042a54b97e31af4529ea0db79ba2c4c2bb6cc
      """.stripMargin

    val checksum = Some("563042a54b97e31af4529ea0db79ba2c4c2bb6cc")

    'parseJunkChecksum{
      assert(Cache.parseChecksum(junkSha1) == checksum)
    }

    'parseCleanChecksum{
      assert(Cache.parseChecksum(cleanSha1) == checksum)
    }


    val artifact = Artifact(
      "http://abc.com/test.jar",
      Map (
        "MD5" -> "http://abc.com/test.jar.md5",
        "SHA-1" -> "http://abc.com/test.jar.sha1"
      ),
      Map.empty,
      Attributes("jar"),
      changing = false
    )

    val pool = Executors.newFixedThreadPool(1)
    val cachePath = getClass.getResource("/checksums").getPath

    val cache = Seq(
      "http://" -> new File(cachePath),
      "https://" -> new File(cachePath)
    )

    'sha1{
      val res = Cache.validateChecksum(
        artifact,
        "SHA-1",
        cache,
        pool
      )
      assert(res.run.run.isRight)
    }

    'md5{
      val res = Cache.validateChecksum(
        artifact,
        "MD5",
        cache,
        pool
      )

      assert(res.run.run.isRight)
    }
  }
}