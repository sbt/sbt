package coursier
package test

import java.io.File
import java.nio.file.Files

import coursier.cache.protocol.TestprotocolHandler
import coursier.interop.scalaz._
import utest._

import scala.util.Try

object CacheFetchTests extends TestSuite {

  def check(extraRepo: Repository): Unit = {

    val tmpDir = Files.createTempDirectory("coursier-cache-fetch-tests").toFile

    def cleanTmpDir() = {
      def delete(f: File): Boolean =
        if (f.isDirectory) {
          val removedContent = Option(f.listFiles()).toSeq.flatten.map(delete).forall(x => x)
          val removedDir = f.delete()

          removedContent && removedDir
        } else
          f.delete()

      if (!delete(tmpDir))
        Console.err.println(s"Warning: unable to remove temporary directory $tmpDir")
    }

    val res = try {
      val fetch = Fetch.from(
        Seq(
          extraRepo,
          MavenRepository("https://repo1.maven.org/maven2")
        ),
        Cache.fetch(
          tmpDir
        )
      )

      val startRes = Resolution(
        Set(
          Dependency(
            Module("com.github.alexarchambault", "coursier_2.11"), "1.0.0-M9-test"
          )
        )
      )

      startRes.process.run(fetch).unsafePerformSync
    } finally {
      cleanTmpDir()
    }

    val errors = res.errors

    assert(errors.isEmpty)
  }

  val tests = TestSuite {

    // using scala-test would allow to put the below comments in the test names...

    * - {
      // test that everything's fine with basic file protocol
      val repoPath = new File(getClass.getResource("/test-repo/http/abc.com").getPath)
      check(MavenRepository(repoPath.toURI.toString))
    }

    'customProtocol - {
      * - {
        // test the Cache.url method
        val shouldFail = Try(Cache.url("notfoundzzzz://foo/bar"))
        assert(shouldFail.isFailure)

        Cache.url("testprotocol://foo/bar")
      }

      * - {
        // the real custom protocol test
        check(MavenRepository(s"${TestprotocolHandler.protocol}://foo/"))
      }
    }
  }

}
