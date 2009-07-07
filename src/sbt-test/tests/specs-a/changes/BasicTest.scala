import org.specs._

object BasicTest extends Specification
{
 // from specs example
  "'hello world' has 11 characters" in {
     "hello world".size mustEqual 11
  }
  "'hello world' matches 'h.* w.*'" in {
     "hello world" must beMatching("h.* w.*")
  }
}

trait TraitNotATest extends Specification
{
	// would fail if called
  "'hello world' has 11 characters" in {
     "hello world".size mustEqual 12
  }
}

abstract class AbstractNotATest extends Specification
{
	// would fail if called
  "'hello world' has 11 characters" in {
     "hello world".size mustEqual 12
  }
}

class ClassNotATest extends Specification
{
	// would fail if called
  "'hello world' has 11 characters" in {
     "hello world".size mustEqual 12
  }
}