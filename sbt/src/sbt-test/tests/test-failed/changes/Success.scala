import org.specs._

class helloWorldSuccess extends Specification {
  "'hello world' has 11 characters" in {
     "hello world".size must be equalTo(11)
  }
  "'hello world' matches 'h.* w.*'" in {
     "hello world" must be matching("h.* w.*")
  }
}