import org.scalatest.FlatSpec
import java.io.File

trait Ensemble extends FlatSpec {
	def i: Int
	def prefix = System.getProperty("group.prefix")

	"an ensemble" should "create all files" in {
		val f = new File(prefix + i)
		f.createNewFile
	}
}

class Ensemble1 extends Ensemble { def i = 1 }
class Ensemble2 extends Ensemble { def i = 2 }
class Ensemble3 extends Ensemble { def i = 3 }