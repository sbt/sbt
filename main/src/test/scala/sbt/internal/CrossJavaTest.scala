/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import org.scalatest.diagrams.Diagrams
import org.scalatest.funsuite.AnyFunSuite
import sbt.internal.CrossJava.JavaDiscoverConfig.*
import scala.collection.immutable.ListMap

class CrossJavaTest extends AnyFunSuite with Diagrams {
  test("The Java home selector should select the most recent") {
    assert(
      List("jdk1.8.0.jdk", "jdk1.8.0_121.jdk", "jdk1.8.0_45.jdk")
        .sortWith(CrossJava.versionOrder)
        .last == "jdk1.8.0_121.jdk"
    )
  }

  test("The Linux Java home selector should correctly pick up Fedora Java installations") {
    val conf = new LinuxDiscoverConfig(sbt.io.syntax.file(".")) {
      override def candidates(): Vector[String] =
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
    assert(version == "1.8")
    assert(file.getName == "java-1.8.0-openjdk-1.8.0.172-9.b11.fc28.x86_64")
  }

  test("The Linux Java home selector should correctly pick up Oracle RPM installations") {
    val conf = new LinuxDiscoverConfig(sbt.io.syntax.file(".")) {
      override def candidates(): Vector[String] = Vector("jdk1.8.0_172-amd64")
    }
    val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
    assert(version == "1.8")
    assert(file.getName == "jdk1.8.0_172-amd64")
  }

  test("The Windows Java home selector should correctly pick up a JDK") {
    val conf = new WindowsDiscoverConfig(sbt.io.syntax.file(".")) {
      override def candidates() = Vector("jdk1.7.0")
    }
    val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
    assert(version == "1.7")
    assert(file.getName == "jdk1.7.0")
  }

  test("The Windows Java home selector should correctly pick up a JDK with vendors") {
    val conf = new WindowsDiscoverConfig(sbt.io.syntax.file("."), Seq("xxx", "yyy")) {
      override def candidates() = Vector("jdk1.7.0")
    }
    val homes = conf.javaHomes
    assert(homes.size == 2)
    assert(homes.map(_._1) == Vector("xxx@1.7", "yyy@1.7"))
    assert(homes.map(_._2.getName).forall(_ == "jdk1.7.0"))
  }

  test("The JAVA_HOME selector should correctly pick up a JDK") {
    val conf = new JavaHomeDiscoverConfig {
      override def home() = Some("/opt/jdk8")
    }
    val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
    assert(version == "8")
    assert(file.getName == "jdk8")
  }

  test("The JAVA_HOME selector should correctly pick up an Oracle JDK") {
    val conf = new JavaHomeDiscoverConfig {
      override def home() = Some("/opt/oracle-jdk-bin-1.8.0.181")
    }
    val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
    assert(version == "1.8")
    assert(file.getName == "oracle-jdk-bin-1.8.0.181")
  }

  test("The SDKMAN selector should correctly pick up an AdoptOpenJDK") {
    val conf = new SdkmanDiscoverConfig {
      override def candidates() = Vector("11.0.2.hs-adpt")
    }
    val (version, file) = conf.javaHomes.sortWith(CrossJava.versionOrder).last
    assert(version == "adopt@11.0.2")
    assert(file.getName == "11.0.2.hs-adpt")
  }

  test("expandJavaHomes") {
    val conf = new SdkmanDiscoverConfig {
      override def candidates() = Vector("11.0.2.hs-adpt")
    }
    val hs = CrossJava.expandJavaHomes(ListMap(conf.javaHomes*))
    assert(hs.contains("11"))
  }

  test("SDKMAN candidate parsing") {
    assert(
      CrossJava
        .parseSdkmanString("11.0.2.hs-adpt")
        .get == JavaVersion(Vector(11L, 0L, 2L), Some("adopt"))
    )
    assert(
      CrossJava
        .parseSdkmanString("11.0.2.j9-adpt")
        .get == JavaVersion(
        Vector(11L, 0L, 2L),
        Some("adopt-openj9")
      )
    )
    assert(
      CrossJava
        .parseSdkmanString("13.ea.13-open")
        .get == JavaVersion(
        Vector(13L),
        Vector("ea13"),
        Some("openjdk")
      )
    )
    assert(
      CrossJava
        .parseSdkmanString("12.0.0-zulu")
        .get == JavaVersion(
        Vector(12L, 0L, 0L),
        Some("zulu")
      )
    )
    assert(
      CrossJava
        .parseSdkmanString("8u152-zulu")
        .get == JavaVersion(
        Vector(8L, 0L, 152L),
        Vector(),
        Some("zulu")
      )
    )
    assert(
      CrossJava
        .parseSdkmanString("8x152-zulu")
        .isFailure
    )
    assert(
      CrossJava
        .parseSdkmanString("8.0.201-oracle")
        .get == JavaVersion(
        Vector(8L, 0L, 201L),
        Some("oracle")
      )
    )
    assert(
      CrossJava
        .parseSdkmanString("1.0.0-rc-14-grl")
        .get == JavaVersion(
        Vector(1L, 0L, 0L),
        Vector("rc14"),
        Some("graalvm")
      )
    )
  }

  test("SDKMAN version can round trip") {
    assert(
      CrossJava.parseJavaVersion(
        CrossJava
          .parseSdkmanString("11.0.2.hs-adpt")
          .get
          .toString
      ) == JavaVersion(Vector(11L, 0L, 2L), Some("adopt"))
    )

    assert(
      CrossJava.parseJavaVersion(
        CrossJava
          .parseSdkmanString("1.0.0-rc-14-grl")
          .get
          .toString
      ) == JavaVersion(
        Vector(1L, 0L, 0L),
        Vector("rc14"),
        Some("graalvm")
      )
    )
  }
}
