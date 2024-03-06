package sbt

import java.nio.file._
import scala.collection.JavaConverters._

object TestMain {
  def main(args: Array[String]): Unit = {
    val libraryPath = System.getProperty("java.library.path")
    System.loadLibrary("sbt-jni-library-test0")
    println(s"Native value is ${new JniLibrary().getIntegerValue}")
  }
}