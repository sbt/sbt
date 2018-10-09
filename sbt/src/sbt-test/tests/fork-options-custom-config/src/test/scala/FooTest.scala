package foo.test

import org.scalatest._

class FooTest extends FunSpec with Matchers {

	if(java.lang.Boolean.getBoolean("test.init.fail"))
		sys.error("exception during construction")

	describe("Foo.foo should") {
		it("always return 5") {
			Foo.foo should equal (5)
		}
	}
}
