import org.scalatest.FlatSpec
import java.io.File

class CheckSetupCleanup extends FlatSpec {
	val f = new File("setup")
	"setup file" should "exist" in {
		assert(f.exists, "Setup file didn't exist: " + f.getAbsolutePath)
		f.delete()
	}

	val t = new File("tested")
	"cleanup file" should "not exist" in {
		assert(!t.exists, "Cleanup file already existed: " + t.getAbsolutePath)
		t.createNewFile()
	}
}
