import org.scalatest.FlatSpec
import org.scalatest.matchers.MustMatchers
import java.io.File

class CheckSetupCleanup extends FlatSpec with MustMatchers
{
	val f = new File("setup")
	"setup file" must "exist" in {
		assert(f.exists, "Setup file didn't exist: " + f.getAbsolutePath)
		f.delete()
	}

	val t = new File("tested")
	"cleanup file" must "not exist" in {
		assert(!t.exists, "Cleanup file already existed: " + t.getAbsolutePath)
		t.createNewFile()
	}
}
