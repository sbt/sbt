package coursier.util

import coursier.{Attributes, Dependency}
import coursier.core.{Module, Repository}
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository

import scala.collection.mutable.ArrayBuffer

object Parse {

  private def defaultScalaVersion = scala.util.Properties.versionNumberString

  @deprecated("use the variant accepting a default scala version", "1.0.0-M13")
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

  @deprecated("use the variant accepting a default scala version", "1.0.0-M13")
  def modules(l: Seq[String]): (Seq[String], Seq[Module]) =
    modules(l, defaultScalaVersion)

  /**
    * Parses a sequence of coordinates.
    *
    * @return Sequence of errors, and sequence of modules/versions
    */
  def modules(l: Seq[String], defaultScalaVersion: String): (Seq[String], Seq[Module]) =
    valuesAndErrors(module(_, defaultScalaVersion), l)

  @deprecated("use the variant accepting a default scala version", "1.0.0-M13")
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

  class ModuleParseError(private val message: String = "",
                              private val cause: Throwable = None.orNull)
    extends Exception(message, cause)

  @deprecated("use the variant accepting a default scala version", "1.0.0-M13")
  def moduleVersionConfig(s: String, defaultScalaVersion: String): Either[String, (Module, String, Option[String])] = {
    val mvc: Either[String, (Dependency, Map[String, String])] =
      moduleVersionConfig(s, ModuleRequirements(), transitive = true, defaultScalaVersion)
    mvc match {
      case Left(x) => Left(x)
      case Right(depsWithParams) =>
        val (dep, _) = depsWithParams
        Right(dep.module, dep.version, Option(dep.configuration).filter(_.trim.nonEmpty))
    }
  }


  @deprecated("use the variant accepting a default scala version", "1.0.0-M13")
  def moduleVersionConfig(s: String): Either[String, (Module, String, Option[String])] = {
    val mvc: Either[String, (Dependency, Map[String, String])] =
      moduleVersionConfig(s, ModuleRequirements(), transitive = true, defaultScalaVersion)
    mvc match {
      case Left(x) => Left(x)
      case Right(depsWithParams) =>
        val (dep, _) = depsWithParams
        Right(dep.module, dep.version, Option(dep.configuration).filter(_.trim.nonEmpty))
    }
  }

  /**
    * Parses coordinates like
    *   org:name:version
    *  with attributes, like
    *   org:name:version,attr1=val1,attr2=val2
    *  and a configuration, like
    *   org:name:version:config
    *  or
    *   org:name:version:config,attr1=val1,attr2=val2
    *
    *  Currently only the "classifier" and "url attributes are
    *  used, and others throw errors.
    */
  def moduleVersionConfig(s: String,
                          req: ModuleRequirements,
                          transitive: Boolean,
                          defaultScalaVersion: String): Either[String, (Dependency, Map[String, String])] = {

    // Assume org:name:version,attr1=val1,attr2=val2
    // That is ',' has to go after ':'.
    // E.g. "org:name,attr1=val1,attr2=val2:version:config" is illegal.
    val attrSeparator = ","
    val argSeparator = ":"

    val Array(coords, rawAttrs @ _*) = s.split(attrSeparator)

    val attrsOrErrors = rawAttrs
      .map { x =>
        if (x.contains(argSeparator))
          Left(s"'$argSeparator' is not allowed in attribute '$x' in '$s'. Please follow the format " +
            s"'org${argSeparator}name[${argSeparator}version][${argSeparator}config]${attrSeparator}attr1=val1${attrSeparator}attr2=val2'")
        else
          x.split("=") match {
            case Array(k, v) =>
              Right(k -> v)
            case _ =>
              Left(s"Failed to parse attribute '$x' in '$s'. Keyword argument expected such as 'classifier=tests'")
          }
      }

    attrsOrErrors
      .collectFirst {
        case Left(err) => Left(err)
      }
      .getOrElse {

        val attrs = attrsOrErrors
          .collect {
            case Right(attr) => attr
          }
          .toMap

        val parts = coords.split(":", 5)

        // Only "classifier" and "url" attributes are allowed
        val validAttrsKeys = Set("classifier", "url")

        validateAttributes(attrs, s, validAttrsKeys) match {
          case Some(err) => Left(err)
          case None =>

            val attributes = attrs.get("classifier") match {
              case Some(c) => Attributes("", c)
              case None => Attributes("", "")
            }

            val extraDependencyParams: Map[String, String] = attrs.get("url") match {
                case Some(url) => Map("url" -> url)
                case None => Map()
              }

            val localExcludes = req.localExcludes
            val globalExcludes = req.globalExcludes
            val defaultConfig = req.defaultConfiguration

            val depOrError = parts match {
              case Array(org, "", rawName, version, config) =>
                module(s"$org::$rawName", defaultScalaVersion)
                  .right
                  .map(mod => {
                    Dependency(
                      mod,
                      version,
                      config,
                      attributes,
                      transitive = transitive,
                      exclusions = localExcludes.getOrElse(mod.orgName, Set()) | globalExcludes)
                  })

              case Array(org, "", rawName, version) =>
                module(s"$org::$rawName", defaultScalaVersion)
                  .right
                  .map(mod => {
                    Dependency(
                      mod,
                      version,
                      configuration = defaultConfig,
                      attributes = attributes,
                      transitive = transitive,
                      exclusions = localExcludes.getOrElse(mod.orgName, Set()) | globalExcludes)
                  })

              case Array(org, rawName, version, config) =>
                module(s"$org:$rawName", defaultScalaVersion)
                  .right
                  .map(mod => {
                    Dependency(
                      mod,
                      version,
                      config,
                      attributes,
                      transitive = transitive,
                      exclusions = localExcludes.getOrElse(mod.orgName, Set()) | globalExcludes)
                  })

              case Array(org, rawName, version) =>
                module(s"$org:$rawName", defaultScalaVersion)
                  .right
                  .map(mod => {
                    Dependency(
                      mod,
                      version,
                      configuration = defaultConfig,
                      attributes = attributes,
                      transitive = transitive,
                      exclusions = localExcludes.getOrElse(mod.orgName, Set()) | globalExcludes)
                  })

              case _ =>
                Left(s"Malformed dependency: $s")
            }

            depOrError.right.map(dep => (dep, extraDependencyParams))
        }
    }
  }

  /**
   * Validates the parsed attributes
   *
   * Currently only "classifier" and "url" are allowed. If more are
   * added, they should be passed in via the second parameter
   *
   * @param attrs Attributes parsed
   * @param dep String representing the dep being parsed
   * @param validAttrsKeys Valid attribute keys
   * @return A string if there is an error, otherwise None
   */
  private def validateAttributes(attrs: Map[String, String],
                                 dep: String,
                                 validAttrsKeys: Set[String]): Option[String] = {
    val extraAttributes = attrs.keys.toSet.diff(validAttrsKeys)

    if (attrs.size > validAttrsKeys.size || extraAttributes.nonEmpty)
      Some(s"The only attributes allowed are: ${validAttrsKeys.mkString(", ")}. ${
        if (extraAttributes.nonEmpty) s"The following are invalid: " +
          s"${extraAttributes.map(_ + s" in "+ dep).mkString(", ")}"
      }")
    else None
  }

  @deprecated("use the variant accepting a default scala version", "1.0.0-M13")
  def moduleVersions(l: Seq[String]): (Seq[String], Seq[(Module, String)]) =
    moduleVersions(l, defaultScalaVersion)

  /**
    * Parses a sequence of coordinates.
    *
    * @return Sequence of errors, and sequence of modules / versions
    */
  def moduleVersions(l: Seq[String], defaultScalaVersion: String): (Seq[String], Seq[(Module, String)]) =
    valuesAndErrors(moduleVersion(_, defaultScalaVersion), l)

  @deprecated("use the variant accepting a default scala version", "1.0.0-M13")
  def moduleVersionConfigs(l: Seq[String]): (Seq[String], Seq[(Module, String, Option[String])]) = {
    val mvc: (Seq[String], Seq[(Dependency, Map[String, String])]) =
      moduleVersionConfigs(l, ModuleRequirements(), transitive = true, defaultScalaVersion)
    val errorsAndDeps = (mvc._1, mvc._2.map(d => d._1))
    // convert empty config to None
    (errorsAndDeps._1, errorsAndDeps._2.map(d => (d.module, d.version, Option(d.configuration).filter(_.trim.nonEmpty))))
  }

  @deprecated("use the variant accepting a default scala version", "1.0.0-M13")
  def moduleVersionConfigs(l: Seq[String], defaultScalaVersion: String): (Seq[String], Seq[(Module, String, Option[String])]) = {
    val mvc: (Seq[String], Seq[(Dependency, Map[String, String])]) =
      moduleVersionConfigs(l, ModuleRequirements(), transitive = true, defaultScalaVersion)
    val errorsAndDeps = (mvc._1, mvc._2.map(d => d._1))
    (errorsAndDeps._1, errorsAndDeps._2.map(d => (d.module, d.version, Option(d.configuration).filter(_.trim.nonEmpty))))
  }

  /**
    * Data holder for additional info that needs to be considered when parsing the module.
    *
    * @param globalExcludes global excludes that need to be applied to all modules
    * @param localExcludes excludes to be applied to specific modules
    * @param defaultConfiguration default configuration
    */
  case class ModuleRequirements(globalExcludes: Set[(String, String)] = Set(),
                                localExcludes: Map[String, Set[(String, String)]] = Map(),
                                defaultConfiguration: String = "default(compile)")

  /**
    * Parses a sequence of coordinates having an optional configuration.
    *
    * @return Sequence of errors, and sequence of modules / versions / optional configurations
    */
  def moduleVersionConfigs(l: Seq[String],
                           req: ModuleRequirements,
                           transitive: Boolean,
                           defaultScalaVersion: String): (Seq[String], Seq[(Dependency, Map[String, String])]) =
    valuesAndErrors(moduleVersionConfig(_, req, transitive, defaultScalaVersion), l)

  def repository(s: String): Either[String, Repository] =
    if (s == "central")
      Right(MavenRepository("https://repo1.maven.org/maven2"))
    else if (s.startsWith("sonatype:"))
      Right(MavenRepository(s"https://oss.sonatype.org/content/repositories/${s.stripPrefix("sonatype:")}"))
    else if (s.startsWith("bintray:"))
      Right(MavenRepository(s"https://dl.bintray.com/${s.stripPrefix("bintray:")}"))
    else if (s.startsWith("bintray-ivy:"))
      Right(IvyRepository.fromPattern(
        s"https://dl.bintray.com/${s.stripPrefix("bintray-ivy:").stripSuffix("/")}/" +:
          coursier.ivy.Pattern.default
      ))
    else if (s.startsWith("typesafe:ivy-"))
      Right(IvyRepository.fromPattern(
        s"https://repo.typesafe.com/typesafe/ivy-${s.stripPrefix("typesafe:ivy-")}/" +:
          coursier.ivy.Pattern.default
      ))
    else if (s.startsWith("typesafe:"))
      Right(MavenRepository(s"https://repo.typesafe.com/typesafe/${s.stripPrefix("typesafe:")}"))
    else if (s.startsWith("sbt-plugin:"))
      Right(IvyRepository.fromPattern(
        s"https://repo.scala-sbt.org/scalasbt/sbt-plugin-${s.stripPrefix("sbt-plugin:")}/" +:
          coursier.ivy.Pattern.default
      ))
    else if (s.startsWith("ivy:"))
      IvyRepository.parse(s.stripPrefix("ivy:"))
    else if (s == "jitpack")
      Right(MavenRepository("https://jitpack.io"))
    else
      Right(MavenRepository(s))

}
