import org.scalatest._
import org.scalatest.matchers._

class IgnoreTest extends Spec with BeforeAndAfter with MustMatchers {

        describe("This test") {
                ignore("should be ignored") {
                   error("Test ran")
                }
        }
}