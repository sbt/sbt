package coursier.util

import coursier.core.{Repository, Module}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository

import scala.collection.mutable.ArrayBuffer

object Parse {

  /**
    * Parses a module like
    *   org:name
    *  possibly with attributes, like
    *    org:name;attr1=val1;attr2=val2
    */
  def module(s: String): Either[String, Module] = {

    val parts = s.split(":", 2)

    parts match {
      case Array(org, rawName) =>
        val splitName = rawName.split(';')

        if (splitName.tail.exists(!_.contains("=")))
          Left(s"malformed attribute(s) in $s")
        else {
          val name = splitName.head
          val attributes = splitName.tail.map(_.split("=", 2)).map {
            case Array(key, value) => key -> value
          }.toMap

          Right(Module(org, name, attributes))
        }

      case _ =>
        Left(s"malformed module: $s")
    }
  }

  private def valuesAndErrors[L, R](f: String => Either[L, R], l: Seq[String]): (Seq[L], Seq[R]) = {

    val errors = new ArrayBuffer[L]
    val values = new ArrayBuffer[R]

    for (elem <- l)
      f(elem) match {
        case Left(err) => errors += err
        case Right(modVer) => values += modVer
      }

    (errors.toSeq, values.toSeq)
  }

  /**
    * Parses a sequence of coordinates.
    *
    * @return Sequence of errors, and sequence of modules/versions
    */
  def modules(l: Seq[String]): (Seq[String], Seq[Module]) =
    valuesAndErrors(module, l)

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
         module(s"$org:$rawName")
           .right
           .map((_, version))

      case _ =>
        Left(s"Malformed coordinates: $s")
    }
  }

  /**
    * Parses coordinates like
    *   org:name:version
    *  possibly with attributes, like
    *   org:name;attr1=val1;attr2=val2:version
    *  and a configuration, like
    *   org:name:version:config
    *  or
    *   org:name;attr1=val1;attr2=val2:version:config
    */
  def moduleVersionConfig(s: String): Either[String, (Module, String, Option[String])] = {

    val parts = s.split(":", 4)

    parts match {
      case Array(org, rawName, version, config) =>
        module(s"$org:$rawName")
          .right
          .map((_, version, Some(config)))

      case Array(org, rawName, version) =>
        module(s"$org:$rawName")
          .right
          .map((_, version, None))

      case _ =>
        Left(s"Malformed coordinates: $s")
    }
  }

  /**
    * Parses a sequence of coordinates.
    *
    * @return Sequence of errors, and sequence of modules / versions
    */
  def moduleVersions(l: Seq[String]): (Seq[String], Seq[(Module, String)]) =
    valuesAndErrors(moduleVersion, l)

  /**
    * Parses a sequence of coordinates having an optional configuration.
    *
    * @return Sequence of errors, and sequence of modules / versions / optional configurations
    */
  def moduleVersionConfigs(l: Seq[String]): (Seq[String], Seq[(Module, String, Option[String])]) =
    valuesAndErrors(moduleVersionConfig, l)

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
