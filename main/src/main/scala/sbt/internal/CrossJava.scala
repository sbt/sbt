/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import java.io.File
import scala.collection.immutable.ListMap
import sbt.io.IO
import sbt.io.syntax._

private[sbt] object CrossJava {
  def discoverJavaHomes: ListMap[JavaVersion, File] = {
    val configs = Vector(JavaDiscoverConfig.linux, JavaDiscoverConfig.macOS)
    ListMap(configs flatMap { _.javaHomes }: _*)
  }

  sealed trait JavaDiscoverConf {
    def javaHomes: Vector[(JavaVersion, File)]
  }

  object JavaDiscoverConfig {
    val linux = new JavaDiscoverConf {
      val base: File = file("/usr") / "lib" / "jvm"
      val JavaHomeDir = """java-([0-9]+)-.*""".r
      def javaHomes: Vector[(JavaVersion, File)] =
        wrapNull(base.list()).collect {
          case dir @ JavaHomeDir(ver) => JavaVersion(ver) -> (base / dir)
        }
    }

    val macOS = new JavaDiscoverConf {
      val base: File = file("/Library") / "Java" / "JavaVirtualMachines"
      val JavaHomeDir = """jdk-?(1\.)?([0-9]+).*""".r
      def javaHomes: Vector[(JavaVersion, File)] =
        wrapNull(base.list()).collect {
          case dir @ JavaHomeDir(m, n) =>
            JavaVersion(n) -> (base / dir / "Contents" / "Home")
        }
    }
  }

  def wrapNull(a: Array[String]): Vector[String] =
    if (a eq null) Vector()
    else a.toVector
}
