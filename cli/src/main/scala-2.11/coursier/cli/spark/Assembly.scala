package coursier.cli.spark

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.zip.{ZipInputStream, ZipOutputStream}

import coursier.Dependency

object Assembly {

  sealed abstract class Rule extends Product with Serializable

  object Rule {
    case class Exclude(path: String) extends Rule
    case class Append(path: String) extends Rule
  }

  def make(jars: Seq[File], output: File, rules: Seq[Rule]): Unit = {

    val zos = new ZipOutputStream(new FileOutputStream(output))

    for (jar <- jars) {
      new ZipInputStream(new FileInputStream(jar))
    }

    ???
  }

  def spark(
    scalaVersion: String,
    sparkVersion: String,
    noDefault: Boolean,
    extraDependencies: Seq[Dependency]
  ): Either[String, (File, Seq[File])] =
    throw new Exception("Not implemented: automatic assembly generation")

}
