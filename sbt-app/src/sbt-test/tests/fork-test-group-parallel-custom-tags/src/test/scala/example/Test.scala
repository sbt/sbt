package example

import java.io.File
import org.specs2.mutable.Specification

trait Test extends Specification {
  def testType: String
  "spec" should {
    "be run one at time" in {
      val lock = new File(s"lock${testType}")
      println(lock)
      lock.mkdir() should beTrue
      Thread.sleep(2000)
      lock.delete() should beTrue
    }
  }
}

class TestA0 extends Test {
  override def testType: String = "A"
}

class TestA1 extends Test {
  override def testType: String = "A"
}

class TestB0 extends Test {
  override def testType: String = "B"
}

class TestB1 extends Test {
  override def testType: String = "B"
}
