package sbt.internal

import org.specs2.mutable.Specification

class CrossJavaTest extends Specification {
  "The Java home selector" should {
    "select the most recent" in {
      List("jdk1.8.0.jdk", "jdk1.8.0_121.jdk", "jdk1.8.0_45.jdk")
        .sortWith(CrossJava.versionOrder)
        .last must be equalTo ("jdk1.8.0_121.jdk")
    }
  }
}
