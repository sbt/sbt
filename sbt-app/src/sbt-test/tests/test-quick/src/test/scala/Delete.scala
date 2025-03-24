import org.scalatest.FlatSpec

class Delete extends FlatSpec with Base {
  "a file" should "exist" in {
		assert(marker.exists == true)
		marker.delete()
  }

}
