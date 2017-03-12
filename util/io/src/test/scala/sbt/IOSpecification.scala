package sbt

import util.Try

import org.scalacheck._
import Prop._

object IOSpecification extends Properties("IO") {
  property("classLocation able to determine containing directories") = Prop.forAll(classes) { (c: Class[_]) =>
    Try(IO.classLocationFile(c)).toOption.exists {
      case jar if jar.getName.endsWith(".jar") => jar.isFile
      case dir                                 => dir.isDirectory
    }
  }

  implicit def classes: Gen[Class[_]] =
    Gen.oneOf(
      this.getClass,
      classOf[java.lang.Integer],
      classOf[java.util.AbstractMap.SimpleEntry[String, String]],
      classOf[String],
      classOf[Thread],
      classOf[Properties]
    )
}
