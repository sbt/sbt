package lmcoursier.internal

import java.net.MalformedURLException
import java.nio.file.Paths

import coursier.cache.CacheUrl
import coursier.core.{ Authentication, Repository }
import coursier.ivy.IvyRepository
import coursier.maven.SbtMavenRepository
import sbt.librarymanagement.*
import sbt.util.Logger

import scala.jdk.CollectionConverters.*

object Resolvers {

  private def mavenCompatibleBaseOpt(patterns: Patterns): Option[String] =
    if (patterns.isMavenCompatible) {
      // input  : /Users/user/custom/repo/[organisation]/[module](_[scalaVersion])(_[sbtVersion])/[revision]/[artifact]-[revision](-[classifier]).[ext]
      // output : /Users/user/custom/repo/
      def basePattern(pattern: String): String = pattern.takeWhile(c => c != '[' && c != '(')

      val baseIvyPattern = basePattern(patterns.ivyPatterns.head)
      val baseArtifactPattern = basePattern(patterns.artifactPatterns.head)

      if (baseIvyPattern == baseArtifactPattern)
        Some(baseIvyPattern)
      else
        None
    } else
      None

  private def mavenRepositoryOpt(
      root: String,
      log: Logger,
      authentication: Option[Authentication],
      classLoaders: Seq[ClassLoader]
  ): Option[SbtMavenRepository] =
    try {
      CacheUrl.url(root, classLoaders) // ensure root is a URL whose protocol can be handled here
      val root0 = if (root.endsWith("/")) root else root + "/"
      Some(
        SbtMavenRepository(
          root0,
          authentication = authentication
        )
      )
    } catch {
      case e: MalformedURLException =>
        log.warn(
          "Error parsing Maven repository base " +
            root +
            Option(e.getMessage).fold("")(" (" + _ + ")") +
            ", ignoring it"
        )

        None
    }

  // this handles whitespace in path
  private def pathToUriString(path: String): String = {
    val stopAtIdx = path.indexWhere(c => c == '[' || c == '$' || c == '(')
    if (stopAtIdx > 0) {
      val (pathPart, patternPart) = path.splitAt(stopAtIdx)
      Paths.get(pathPart).toUri.toASCIIString + patternPart
    } else if (stopAtIdx == 0)
      "file://" + path
    else
      Paths.get(path).toUri.toASCIIString
  }

  def repository(
      resolver: Resolver,
      ivyProperties: Map[String, String],
      log: Logger,
      authentication: Option[Authentication],
      classLoaders: Seq[ClassLoader]
  ): Option[Repository] =
    resolver match {
      case r: sbt.librarymanagement.MavenRepository =>
        mavenRepositoryOpt(r.root, log, authentication, classLoaders)

      case r: FileRepository
          if r.patterns.ivyPatterns.lengthCompare(1) == 0 &&
            r.patterns.artifactPatterns.lengthCompare(1) == 0 =>
        val mavenCompatibleBaseOpt0 = mavenCompatibleBaseOpt(r.patterns)

        mavenCompatibleBaseOpt0 match {
          case None =>
            val repo = IvyRepository.parse(
              pathToUriString(r.patterns.artifactPatterns.head),
              metadataPatternOpt = Some(pathToUriString(r.patterns.ivyPatterns.head)),
              changing = Some(true),
              properties = ivyProperties,
              dropInfoAttributes = true,
              authentication = authentication
            ) match {
              case Left(err) =>
                sys.error(
                  s"Cannot parse Ivy patterns ${r.patterns.artifactPatterns.head} and ${r.patterns.ivyPatterns.head}: $err"
                )
              case Right(repo) =>
                repo
            }

            Some(repo)

          case Some(mavenCompatibleBase) =>
            mavenRepositoryOpt(
              pathToUriString(mavenCompatibleBase),
              log,
              authentication,
              classLoaders
            )
        }

      case r: URLRepository if patternMatchGuard(r.patterns) =>
        parseMavenCompatResolver(log, ivyProperties, authentication, r.patterns, classLoaders)

      case raw: RawRepository if raw.name == "inter-project" => // sbt.RawRepository.equals just compares names anyway
        None

      // Pattern Match resolver-type-specific RawRepositories
      case IBiblioRepository(p) =>
        parseMavenCompatResolver(log, ivyProperties, authentication, p, classLoaders)

      case other =>
        log.warn(s"Unrecognized repository ${other.name}, ignoring it")
        None
    }

  private object IBiblioRepository {

    // Use reflection to avoid a compile-time dependency on lm-ivy / Apache Ivy.
    // At runtime the class will be present on the classpath via the main module.
    private val ibiblioClass: Option[Class[?]] =
      try Some(Class.forName("org.apache.ivy.plugins.resolver.IBiblioResolver"))
      catch { case _: ClassNotFoundException => None }

    private def stringVector(v: java.util.List[?]): Vector[String] =
      Option(v).map(_.asScala.toVector).getOrElse(Vector.empty).collect { case s: String =>
        s
      }

    private def patternsViaReflection(resolver: AnyRef): Patterns =
      val cls = resolver.getClass
      Patterns(
        ivyPatterns = stringVector(
          cls.getMethod("getIvyPatterns").invoke(resolver).asInstanceOf[java.util.List[?]]
        ),
        artifactPatterns = stringVector(
          cls.getMethod("getArtifactPatterns").invoke(resolver).asInstanceOf[java.util.List[?]]
        ),
        isMavenCompatible = cls.getMethod("isM2compatible").invoke(resolver).asInstanceOf[Boolean],
        descriptorOptional =
          !cls.getMethod("isUseMavenMetadata").invoke(resolver).asInstanceOf[Boolean],
        skipConsistencyCheck =
          !cls.getMethod("isCheckconsistency").invoke(resolver).asInstanceOf[Boolean],
      )

    def unapply(r: Resolver): Option[Patterns] =
      r match {
        case raw: RawRepository =>
          ibiblioClass match
            case Some(cls) if cls.isInstance(raw.resolver) =>
              Some(patternsViaReflection(raw.resolver))
                .filter(patternMatchGuard)
            case _ =>
              None
        case _ =>
          None
      }
  }

  private def patternMatchGuard(patterns: Patterns): Boolean =
    patterns.ivyPatterns.lengthCompare(1) == 0 &&
      patterns.artifactPatterns.lengthCompare(1) == 0

  private def parseMavenCompatResolver(
      log: Logger,
      ivyProperties: Map[String, String],
      authentication: Option[Authentication],
      patterns: Patterns,
      classLoaders: Seq[ClassLoader],
  ): Option[Repository] = {
    val mavenCompatibleBaseOpt0 = mavenCompatibleBaseOpt(patterns)

    mavenCompatibleBaseOpt0 match {
      case None =>
        val repo = IvyRepository.parse(
          patterns.artifactPatterns.head,
          metadataPatternOpt = Some(patterns.ivyPatterns.head),
          changing = None,
          properties = ivyProperties,
          dropInfoAttributes = true,
          authentication = authentication
        ) match {
          case Left(err) =>
            sys.error(
              s"Cannot parse Ivy patterns ${patterns.artifactPatterns.head} and ${patterns.ivyPatterns.head}: $err"
            )
          case Right(repo) =>
            repo
        }

        Some(repo)

      case Some(mavenCompatibleBase) =>
        mavenRepositoryOpt(mavenCompatibleBase, log, authentication, classLoaders)
    }
  }
}
