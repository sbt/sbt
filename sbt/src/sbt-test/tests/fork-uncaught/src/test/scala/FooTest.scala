package foo.test

import org.scalatest.{FunSpec, ShouldMatchers}

class FooTest extends FunSpec with ShouldMatchers {

	if(java.lang.Boolean.getBoolean("test.init.fail"))
		sys.error("exception during construction")

	describe("Foo.foo should") {
		it("always return 5") {
			Foo.foo should equal (5)
		}
	}
}
