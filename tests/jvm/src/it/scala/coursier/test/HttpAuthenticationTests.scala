package coursier.test

import utest._

import coursier.core.Authentication
import coursier.maven.MavenRepository

object HttpAuthenticationTests extends TestSuite {

  val tests = TestSuite {
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
            "  ./coursier launch -r https://dl.bintray.com/scalaz/releases " +
            "io.get-coursier:simple-web-server_2.11:1.0.0-M12 -- " +
            "-d tests/jvm/src/test/resources/test-repo/http/abc.com -u user -P pass -r realm -v"
        )

      * - {
        // no authentication -> should fail

        val failed = try {
          CacheFetchTests.check(
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
          CacheFetchTests.check(
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
