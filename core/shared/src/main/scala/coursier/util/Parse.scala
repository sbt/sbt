package coursier.util

import coursier.core.{Repository, Module}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository

import scala.collection.mutable.ArrayBuffer

object Parse {

  /**
    * Parses coordinates like
    *   org:name:version
    *  possibly with attributes, like
    *    org:name;attr1=val1;attr2=val2:version
    */
  def moduleVersion(s: String): Either[String, (Module, String)] = {

    val parts = s.split(":", 3)

    parts match {
      case Array(org, rawName, version) =>
        val splitName = rawName.split(';')

        if (splitName.tail.exists(!_.contains("=")))
          Left(s"Malformed attribute in $s")
        else {
          val name = splitName.head
          val attributes = splitName.tail.map(_.split("=", 2)).map {
            case Array(key, value) => key -> value
          }.toMap

          Right((Module(org, name, attributes), version))
        }

      case _ =>
        Left(s"Malformed coordinates: $s")
    }
  }

  /**
    * Parses a sequence of coordinates.
    *
    * @return Sequence of errors, and sequence of modules/versions
    */
  def moduleVersions(l: Seq[String]): (Seq[String], Seq[(Module, String)]) = {

    val errors = new ArrayBuffer[String]
    val moduleVersions = new ArrayBuffer[(Module, String)]

    for (elem <- l)
      moduleVersion(elem) match {
        case Left(err) => errors += err
        case Right(modVer) => moduleVersions += modVer
      }

    (errors.toSeq, moduleVersions.toSeq)
  }

  def repository(s: String): Repository =
    if (s == "central")
      MavenRepository("https://repo1.maven.org/maven2")
    else if (s.startsWith("sonatype:"))
      MavenRepository(s"https://oss.sonatype.org/content/repositories/${s.stripPrefix("sonatype:")}")
    else if (s.startsWith("ivy:"))
      IvyRepository(s.stripPrefix("ivy:"))
    else
      MavenRepository(s)

}
