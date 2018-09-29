import java.io.File
import org.scalatest.FlatSpec

class Test1 extends FlatSpec {
  "a test" should "pass" in {
    new File("target/Test1.run").createNewFile()
  }
}

class Test2 extends FlatSpec {
  "a test" should "pass" in {
    new File("target/Test2.run").createNewFile()
  }
}
