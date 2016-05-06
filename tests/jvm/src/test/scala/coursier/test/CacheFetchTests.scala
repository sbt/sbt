package coursier
package test

import java.io.File
import java.nio.file.Files

import coursier.cache.protocol.TestprotocolHandler
import coursier.core.Authentication

import utest._

import scala.util.Try

object CacheFetchTests extends TestSuite {

  val tests = TestSuite {

    def check(extraRepo: Repository): Unit = {

      val tmpDir = Files.createTempDirectory("coursier-cache-fetch-tests").toFile

      def cleanTmpDir() = {
        def delete(f: File): Boolean =
          if (f.isDirectory) {
            val removedContent = f.listFiles().map(delete).forall(x => x)
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

        startRes.process.run(fetch).run
      } finally {
        cleanTmpDir()
      }

      val errors = res.errors

      assert(errors.isEmpty)
    }

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

    'httpAuthentication - {
      // requires an authenticated HTTP server to be running on localhost:8080 with user 'user'
      // and password 'pass'

      val address = "localhost:8080"
      val user = "user"
      val password = "pass"

      def printErrorMessage() =
        Console.err.println(
          Console.RED +
            s"HTTP authentication tests require a running HTTP server on $address, requiring " +
            s"basic authentication with user '$user' and password '$password', serving the right " +
            "files.\n" + Console.RESET +
            "Run one from the coursier sources with\n" +
            "  ./coursier launch -r http://dl.bintray.com/scalaz/releases " +
            "io.get-coursier:simple-web-server_2.11:1.0.0-M12 -- " +
            "-d tests/jvm/src/test/resources/test-repo/http/abc.com -u user -P pass -r realm -v"
        )

      * - {
        // no authentication -> should fail

        val failed = try {
          check(
            MavenRepository(
              s"http://$address"
            )
          )

          printErrorMessage()
          false
        } catch {
          case e: Throwable =>
            true
        }

        assert(failed)
      }

      * - {
        // with authentication -> should work

        try {
          check(
            MavenRepository(
              s"http://$address",
              authentication = Some(Authentication(user, password))
            )
          )
        } catch {
          case e: Throwable =>
            printErrorMessage()
            throw e
        }
      }
    }
  }

}
