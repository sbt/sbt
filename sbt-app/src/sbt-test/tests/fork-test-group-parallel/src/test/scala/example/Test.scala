package example

import java.io.File
import org.specs2.mutable.Specification

trait Test extends Specification {
  "spec" should {
    "be run one at time" in {
      val lock = new File("lock")
      lock.mkdir() should beTrue
      Thread.sleep(2000)
      lock.delete() should beTrue
    }
  }
}

class Test0 extends Test

class Test1 extends Test

class Test2 extends Test

class Test3 extends Test
