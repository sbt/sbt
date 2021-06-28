package sbt

import java.nio.file._
import utest._

object JniLibraryTest extends TestSuite {
  val tests = Tests {
    'load - {
      'native - {
        System.loadLibrary("sbt-jni-library-test0")
        new JniLibrary().getIntegerValue ==> 1
      }
    }
  }
}
