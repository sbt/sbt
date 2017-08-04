import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class Delete extends FlatSpec with ShouldMatchers with Base {
  "a file" should "exist" in {
		marker.exists should equal(true)
		marker.delete()
  }

}
