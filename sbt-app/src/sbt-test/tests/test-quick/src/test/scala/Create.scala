import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class Create extends FlatSpec with ShouldMatchers with Base {
  "a file" should "not exist" in {
		A(new B).foo
		marker.exists should equal(false)
		marker.createNewFile() should equal (true)
  }

}
