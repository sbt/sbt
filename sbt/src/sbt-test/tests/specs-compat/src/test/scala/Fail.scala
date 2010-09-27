import org.specs._

object Failure extends Specification {
  "'hello world' has 11 characters" in {
     "hello world".size must be equalTo(12)
  }
  "'hello world' matches 'h.* w.*'" in {
     "hello world" must be matching("h.* w.*")
  }
}