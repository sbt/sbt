package foo.bar

import org.specs._

class ATest extends Specification
{
	"application and boot classpath" should {
		"be provided to running applications and tests" in {
			val foo = new Foo
			val numbers = List[Any](1,2,5, 19)
			numbers.map(i => foo.eval(i.toString)) must haveTheSameElementsAs(numbers)
		}
	}
}