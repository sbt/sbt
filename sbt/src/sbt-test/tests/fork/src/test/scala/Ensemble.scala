import org.scalatest.FlatSpec
import org.scalatest.matchers.MustMatchers
import java.io.File

class Ensemble extends FlatSpec with MustMatchers {
	val prefix = System.getProperty("group.prefix")
	val countTo = System.getProperty("group.size").toInt

	"an ensemble" must "create all files" in {
		@annotation.tailrec
		def step(i: Int): Unit = {
			val f = new File(prefix + i)
			if (!f.createNewFile)
				step(if (f.exists) i + 1 else i)
			else
				i must be <= (countTo)
		}
		step(1)
	}
}
