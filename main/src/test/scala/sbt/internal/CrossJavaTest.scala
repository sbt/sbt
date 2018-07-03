/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal

import org.specs2.mutable.Specification
import sbt.internal.CrossJava.JavaDiscoverConfig.LinuxDiscoverConfig

class CrossJavaTest extends Specification {
  "The Java home selector" should {
    "select the most recent" in {
      List("jdk1.8.0.jdk", "jdk1.8.0_121.jdk", "jdk1.8.0_45.jdk")
        .sortWith(CrossJava.versionOrder)
        .last must be equalTo ("jdk1.8.0_121.jdk")
    }
  }

  "The Linux Java home selector" should {
    "correctly pick up fedora java installations" in {
      val conf = new LinuxDiscoverConfig(sbt.io.syntax.file(".")) {
        override def candidates: Vector[String] =
          """
            |java-1.8.0-openjdk-1.8.0.162-3.b12.fc28.x86_64
            |java-1.8.0-openjdk-1.8.0.172-9.b11.fc28.x86_64
            |java-1.8.0
            |java-1.8.0-openjdk
            |java-openjdk
            |jre-1.8.0
            |jre-1.8.0-openjdk
            |jre-1.8.0-openjdk-1.8.0.172-9.b11.fc28.x86_64
            |jre-openjdk
          """.stripMargin.split("\n").filter(_.nonEmpty).toVector
      }
      val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
      version must be equalTo ("1.8")
      file.getName must be equalTo ("java-1.8.0-openjdk-1.8.0.172-9.b11.fc28.x86_64")
    }

    "correctly pick up Oracle RPM installations" in {
      val conf = new LinuxDiscoverConfig(sbt.io.syntax.file(".")) {
        override def candidates: Vector[String] = Vector("jdk1.8.0_172-amd64")
      }
      val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
      version must be equalTo ("1.8")
      file.getName must be equalTo ("jdk1.8.0_172-amd64")
    }
  }
}
