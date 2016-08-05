package coursier.util

import coursier.core.{Repository, Module}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository

import scala.collection.mutable.ArrayBuffer

import scalaz.\/
import scalaz.Scalaz.ToEitherOps

object Parse {

  private def defaultScalaVersion = scala.util.Properties.versionNumberString

  @deprecated("use the variant accepting a default scala version")
  def module(s: String): Either[String, Module] =
    module(s, defaultScalaVersion)

  /**
    * Parses a module like
    *   org:name
    *  possibly with attributes, like
    *    org:name;attr1=val1;attr2=val2
    *
    * Two semi-columns after the org part is interpreted as a scala module. E.g. if
    * `defaultScalaVersion` is `"2.11.x"`, org::name:ver is equivalent to org:name_2.11:ver.
    */
  def module(s: String, defaultScalaVersion: String): Either[String, Module] = {

    val parts = s.split(":", 3)

    val values = parts match {
      case Array(org, rawName) =>
        Right((org, rawName, ""))
      case Array(org, "", rawName) =>
        Right((org, rawName, "_" + defaultScalaVersion.split('.').take(2).mkString(".")))
      case _ =>
        Left(s"malformed module: $s")
    }

    values.right.flatMap {
      case (org, rawName, suffix) =>

        val splitName = rawName.split(';')

        if (splitName.tail.exists(!_.contains("=")))
          Left(s"malformed attribute(s) in $s")
        else {
          val name = splitName.head
          val attributes = splitName.tail.map(_.split("=", 2)).map {
            case Array(key, value) => key -> value
          }.toMap

          Right(Module(org, name + suffix, attributes))
        }
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

    (errors, values)
  }

  @deprecated("use the variant accepting a default scala version")
  def modules(l: Seq[String]): (Seq[String], Seq[Module]) =
    modules(l, defaultScalaVersion)

  /**
    * Parses a sequence of coordinates.
    *
    * @return Sequence of errors, and sequence of modules/versions
    */
  def modules(l: Seq[String], defaultScalaVersion: String): (Seq[String], Seq[Module]) =
    valuesAndErrors(module(_, defaultScalaVersion), l)

  @deprecated("use the variant accepting a default scala version")
  def moduleVersion(s: String): Either[String, (Module, String)] =
    moduleVersion(s, defaultScalaVersion)

  /**
    * Parses coordinates like
    *   org:name:version
    *  possibly with attributes, like
    *    org:name;attr1=val1;attr2=val2:version
    */
  def moduleVersion(s: String, defaultScalaVersion: String): Either[String, (Module, String)] = {

    val parts = s.split(":", 4)

    parts match {
      case Array(org, rawName, version) =>
         module(s"$org:$rawName", defaultScalaVersion)
           .right
           .map((_, version))

      case Array(org, "", rawName, version) =>
        module(s"$org::$rawName", defaultScalaVersion)
          .right
          .map((_, version))

      case _ =>
        Left(s"Malformed dependency: $s")
    }
  }

  @deprecated("use the variant accepting a default scala version")
  def moduleVersionConfig(s: String): Either[String, (Module, String, Option[String])] =
    moduleVersionConfig(s, defaultScalaVersion)

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
  def moduleVersionConfig(s: String, defaultScalaVersion: String): Either[String, (Module, String, Option[String])] = {

    val parts = s.split(":", 5)

    parts match {
      case Array(org, "", rawName, version, config) =>
        module(s"$org::$rawName", defaultScalaVersion)
          .right
          .map((_, version, Some(config)))

      case Array(org, "", rawName, version) =>
        module(s"$org::$rawName", defaultScalaVersion)
          .right
          .map((_, version, None))

      case Array(org, rawName, version, config) =>
        module(s"$org:$rawName", defaultScalaVersion)
          .right
          .map((_, version, Some(config)))

      case Array(org, rawName, version) =>
        module(s"$org:$rawName", defaultScalaVersion)
          .right
          .map((_, version, None))

      case _ =>
        Left(s"Malformed dependency: $s")
    }
  }

  @deprecated("use the variant accepting a default scala version")
  def moduleVersions(l: Seq[String]): (Seq[String], Seq[(Module, String)]) =
    moduleVersions(l, defaultScalaVersion)

  /**
    * Parses a sequence of coordinates.
    *
    * @return Sequence of errors, and sequence of modules / versions
    */
  def moduleVersions(l: Seq[String], defaultScalaVersion: String): (Seq[String], Seq[(Module, String)]) =
    valuesAndErrors(moduleVersion(_, defaultScalaVersion), l)

  @deprecated("use the variant accepting a default scala version")
  def moduleVersionConfigs(l: Seq[String]): (Seq[String], Seq[(Module, String, Option[String])]) =
    moduleVersionConfigs(l, defaultScalaVersion)

  /**
    * Parses a sequence of coordinates having an optional configuration.
    *
    * @return Sequence of errors, and sequence of modules / versions / optional configurations
    */
  def moduleVersionConfigs(l: Seq[String], defaultScalaVersion: String): (Seq[String], Seq[(Module, String, Option[String])]) =
    valuesAndErrors(moduleVersionConfig(_, defaultScalaVersion), l)

  def repository(s: String): String \/ Repository =
    if (s == "central")
      MavenRepository("https://repo1.maven.org/maven2").right
    else if (s.startsWith("sonatype:"))
      MavenRepository(s"https://oss.sonatype.org/content/repositories/${s.stripPrefix("sonatype:")}").right
    else if (s.startsWith("bintray:"))
      MavenRepository(s"https://dl.bintray.com/${s.stripPrefix("bintray:")}").right
    else if (s.startsWith("typesafe:ivy-"))
      IvyRepository.fromPattern(
        (s"https://repo.typesafe.com/typesafe/ivy-" + s.stripPrefix("typesafe:ivy-") + "/") +:
          coursier.ivy.Pattern.default
      ).right
    else if (s.startsWith("ivy:"))
      IvyRepository.parse(s.stripPrefix("ivy:"))
    else
      MavenRepository(s).right

}
