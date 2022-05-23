import org.scalatest.FlatSpec

class Create extends FlatSpec with Base {
  "a file" should "not exist" in {
		A(new B).foo
		assert(marker.exists == false)
		assert(marker.createNewFile() == true)
  }

}
