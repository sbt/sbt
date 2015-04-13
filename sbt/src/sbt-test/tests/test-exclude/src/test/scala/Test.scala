import java.io.File
import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class Test1 extends FlatSpec with ShouldMatchers {
  "a test" should "pass" in {
    new File("target/Test1.run").createNewFile()
  }
}

class Test2 extends FlatSpec with ShouldMatchers {
  "a test" should "pass" in {
    new File("target/Test2.run").createNewFile()
  }
}
