package sbt

import java.io.File
import org.specs2._
import mutable.Specification

object CrossVersionTest extends Specification
{
	"Cross version" should {
		"return sbt API for xyz as None" in {
			CrossVersion.sbtApiVersion("xyz") must_== None
		}
		"return sbt API for 0.12 as None" in {
			CrossVersion.sbtApiVersion("0.12") must_== None
		}
		"return sbt API for 0.12.0-SNAPSHOT as None" in {
			CrossVersion.sbtApiVersion("0.12.0-SNAPSHOT") must_== None
		}
		"return sbt API for 0.12.0-RC1 as Some((0, 12))" in {
			CrossVersion.sbtApiVersion("0.12.0-RC1") must_== Some((0, 12))
		}
		"return sbt API for 0.12.0 as Some((0, 12))" in {
			CrossVersion.sbtApiVersion("0.12.0") must_== Some((0, 12))
		}
		"return sbt API for 0.12.1 as Some((0, 12))" in {
			CrossVersion.sbtApiVersion("0.12.1") must_== Some((0, 12))
		}
		"return sbt API compatibility for 0.12.0-M1 as false" in {
			CrossVersion.isSbtApiCompatible("0.12.0-M1") must_== false
		}
		"return sbt API compatibility for 0.12.0-RC1 as true" in {
			CrossVersion.isSbtApiCompatible("0.12.0-RC1") must_== true
		}
		"return binary sbt version for 0.11.3 as 0.11.3" in {
			CrossVersion.binarySbtVersion("0.11.3") must_== "0.11.3"
		}
		"return binary sbt version for 0.12.0-M1 as 0.12.0-M1" in {
			CrossVersion.binarySbtVersion("0.12.0-M1") must_== "0.12.0-M1"
		}
		"return binary sbt version for 0.12.0-RC1 as 0.12" in {
			CrossVersion.binarySbtVersion("0.12.0-RC1") must_== "0.12"
		}
		"return binary sbt version for 0.12.0 as 0.12" in {
			CrossVersion.binarySbtVersion("0.12.0") must_== "0.12"
		}
		"return binary sbt version for 0.12.1 as 0.12" in {
			CrossVersion.binarySbtVersion("0.12.1") must_== "0.12"
		}

		"return Scala API for xyz as None" in {
			CrossVersion.scalaApiVersion("xyz") must_== None
		}
		"return Scala API for 2.10 as None" in {
			CrossVersion.scalaApiVersion("2.10") must_== None
		}
		"return Scala API for 2.10.0-SNAPSHOT as None" in {
			CrossVersion.scalaApiVersion("2.10.0-SNAPSHOT") must_== None
		}
		"return Scala API for 2.10.0-RC1 as None" in {
			CrossVersion.scalaApiVersion("2.10.0-RC1") must_== None
		}
		"return Scala API for 2.10.0 as Some((2, 10))" in {
			CrossVersion.scalaApiVersion("2.10.0") must_== Some((2, 10))
		}
		"return Scala API for 2.10.0-1 as Some((2, 10))" in {
			CrossVersion.scalaApiVersion("2.10.0-1") must_== Some((2, 10))
		}
		"return Scala API for 2.10.1 as Some((2, 10))" in {
			CrossVersion.scalaApiVersion("2.10.1") must_== Some((2, 10))
		}
		"return Scala API compatibility for 2.10.0-M1 as false" in {
			CrossVersion.isScalaApiCompatible("2.10.0-M1") must_== false
		}
		"return Scala API compatibility for 2.10.0-RC1 as false" in {
			CrossVersion.isScalaApiCompatible("2.10.0-RC1") must_== false
		}
		"return binary Scala version for 2.9.2 as 2.9.2" in {
			CrossVersion.binaryScalaVersion("2.9.2") must_== "2.9.2"
		}
		"return binary Scala version for 2.10.0-M1 as 2.10.0-M1" in {
			CrossVersion.binaryScalaVersion("2.10.0-M1") must_== "2.10.0-M1"
		}
		"return binary Scala version for 2.10.0-RC1 as 2.10.0-RC1" in {
			CrossVersion.binaryScalaVersion("2.10.0-RC1") must_== "2.10.0-RC1"
		}
		"return binary Scala version for 2.10.0 as 2.10" in {
			CrossVersion.binaryScalaVersion("2.10.0") must_== "2.10"
		}
		"return binary Scala version for 2.10.1 as 2.10" in {
			CrossVersion.binaryScalaVersion("2.10.1") must_== "2.10"
		}
	}
}
