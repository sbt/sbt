package coursier.maven

import coursier.Fetch
import coursier.core._
import coursier.core.compatibility.encodeURIComponent
import coursier.util.WebPage

import scala.language.higherKinds
import scalaz._

object MavenRepository {
  val SnapshotTimestamp = "(.*-)?[0-9]{8}\\.[0-9]{6}-[0-9]+".r

  def isSnapshot(version: String): Boolean =
    version.endsWith("SNAPSHOT") || SnapshotTimestamp.findFirstIn(version).nonEmpty

  def toBaseVersion(version: String): String = version match {
      case SnapshotTimestamp(null) => "SNAPSHOT"
      case SnapshotTimestamp(base) => base + "SNAPSHOT"
      case _ => version
    }

  def ivyLikePath(
    org: String,
    dirName: String,
    name: String,
    version: String,
    subDir: String,
    baseSuffix: String,
    ext: String
  ) =
    Seq(
      org,
      dirName,
      version,
      subDir,
      s"$name$baseSuffix.$ext"
    )

  def mavenVersioning(
    snapshotVersioning: SnapshotVersioning,
    classifier: String,
    extension: String
  ): Option[String] =
    snapshotVersioning
      .snapshotVersions
      .find(v =>
        (v.classifier == classifier || v.classifier == "*") &&
        (v.extension == extension || v.extension == "*")
       )
      .map(_.value)
      .filter(_.nonEmpty)


  val defaultConfigurations = Map(
    "compile" -> Seq.empty,
    "runtime" -> Seq("compile"),
    "default" -> Seq("runtime"),
    "test" -> Seq("runtime")
  )

  def dirModuleName(module: Module, sbtAttrStub: Boolean): String =
    if (sbtAttrStub) {
      var name = module.name
      for (scalaVersion <- module.attributes.get("scalaVersion"))
        name = name + "_" + scalaVersion
      for (sbtVersion <- module.attributes.get("sbtVersion"))
        name = name + "_" + sbtVersion
      name
    } else
      module.name

}

final case class MavenRepository(
  root: String,
  changing: Option[Boolean] = None,
  /** Hackish hack for sbt plugins mainly - what this does really sucks */
  sbtAttrStub: Boolean = true,
  authentication: Option[Authentication] = None
) extends Repository {

  import Repository._
  import MavenRepository._

  val root0 = if (root.endsWith("/")) root else root + "/"
  val source = MavenSource(root0, changing, sbtAttrStub, authentication)

  private def modulePath(module: Module): Seq[String] =
    module.organization.split('.').toSeq :+ dirModuleName(module, sbtAttrStub)

  private def moduleVersionPath(module: Module, version: String): Seq[String] =
    modulePath(module) :+ toBaseVersion(version)

  private def urlFor(path: Seq[String]): String =
    root0 + path.map(encodeURIComponent).mkString("/")

  def projectArtifact(
    module: Module,
    version: String,
    versioningValue: Option[String]
  ): Artifact = {

    val path = moduleVersionPath(module, version) :+
      s"${module.name}-${versioningValue getOrElse version}.pom"

    Artifact(
      urlFor(path),
      Map.empty,
      Map.empty,
      Attributes("pom", ""),
      changing = changing.getOrElse(isSnapshot(version)),
      authentication = authentication
    )
    .withDefaultChecksums
    .withDefaultSignature
  }

  def versionsArtifact(module: Module): Option[Artifact] = {

    val path = module.organization.split('.').toSeq ++ Seq(
      dirModuleName(module, sbtAttrStub),
      "maven-metadata.xml"
    )

    val artifact =
      Artifact(
        urlFor(path),
        Map.empty,
        Map.empty,
        Attributes("pom", ""),
        changing = true,
        authentication = authentication
      )
      .withDefaultChecksums
      .withDefaultSignature

    Some(artifact)
  }

  def snapshotVersioningArtifact(
    module: Module,
    version: String
  ): Option[Artifact] = {

    val path = moduleVersionPath(module, version) :+ "maven-metadata.xml"

    val artifact =
      Artifact(
        urlFor(path),
        Map.empty,
        Map.empty,
        Attributes("pom", ""),
        changing = true,
        authentication = authentication
      )
      .withDefaultChecksums
      .withDefaultSignature

    Some(artifact)
  }

  private def versionsFromListing[F[_]](
    module: Module,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Versions] = {

    val listingUrl = urlFor(modulePath(module)) + "/"

    // version listing -> changing (changes as new versions are released)
    val listingArtifact = artifactFor(listingUrl, changing = true)

    fetch(listingArtifact).flatMap { listing =>

      val files = WebPage.listFiles(listingUrl, listing)
      val rawVersions = WebPage.listDirectories(listingUrl, listing)

      val res =
        if (files.contains("maven-metadata.xml"))
          -\/("maven-metadata.xml found, not listing version from directory listing")
        else if (rawVersions.isEmpty)
          -\/(s"No versions found at $listingUrl")
        else {
          val parsedVersions = rawVersions.map(Version(_))
          val nonPreVersions = parsedVersions.filter(_.items.forall {
            case q: Version.Qualifier => q.level >= 0
            case _ => true
          })

          if (nonPreVersions.isEmpty)
            -\/(s"Found only pre-versions at $listingUrl")
          else {
            val latest = nonPreVersions.max
            \/-(Versions(
              latest.repr,
              latest.repr,
              nonPreVersions.map(_.repr).toList,
              None
            ))
          }
        }

      EitherT(F.point(res))
    }
  }

  def versions[F[_]](
    module: Module,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Versions] =
    EitherT(
      versionsArtifact(module) match {
        case None => F.point(-\/("Not supported"))
        case Some(artifact) =>
          F.map(fetch(artifact).run)(eitherStr =>
            for {
              str <- eitherStr
              xml <- \/.fromEither(compatibility.xmlParse(str))
              _ <- if (xml.label == "metadata") \/-(()) else -\/("Metadata not found")
              versions <- Pom.versions(xml)
            } yield versions
          )
      }
    )

  def snapshotVersioning[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, SnapshotVersioning] = {

    EitherT(
      snapshotVersioningArtifact(module, version) match {
        case None => F.point(-\/("Not supported"))
        case Some(artifact) =>
          F.map(fetch(artifact).run)(eitherStr =>
            for {
              str <- eitherStr
              xml <- \/.fromEither(compatibility.xmlParse(str))
              _ <- if (xml.label == "metadata") \/-(()) else -\/("Metadata not found")
              snapshotVersioning <- Pom.snapshotVersioning(xml)
            } yield snapshotVersioning
          )
      }
    )
  }

  def findNoInterval[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Project] =
    EitherT {
      def withSnapshotVersioning =
        snapshotVersioning(module, version, fetch).flatMap { snapshotVersioning =>
          val versioningOption =
            mavenVersioning(snapshotVersioning, "", "jar")
              .orElse(mavenVersioning(snapshotVersioning, "", "pom"))
              .orElse(mavenVersioning(snapshotVersioning, "", ""))

          versioningOption match {
            case None =>
              EitherT[F, String, Project](
                F.point(-\/("No snapshot versioning value found"))
              )
            case versioning @ Some(_) =>
              findVersioning(module, version, versioning, fetch)
                .map(_.copy(snapshotVersioning = Some(snapshotVersioning)))
          }
        }

      val res = F.bind(findVersioning(module, version, None, fetch).run) { eitherProj =>
        if (eitherProj.isLeft && isSnapshot(version))
          F.map(withSnapshotVersioning.run)(eitherProj0 =>
            if (eitherProj0.isLeft)
              eitherProj
            else
              eitherProj0
          )
        else
          F.point(eitherProj)
      }

      // keep exact version used to get metadata, in case the one inside the metadata is wrong
      F.map(res)(_.map(proj => proj.copy(actualVersionOpt = Some(version))))
    }

  private def artifactFor(url: String, changing: Boolean) =
    Artifact(
      url,
      Map.empty,
      Map.empty,
      Attributes("", ""),
      changing = changing,
      authentication
    )

  def findVersioning[F[_]](
    module: Module,
    version: String,
    versioningValue: Option[String],
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, Project] = {

    def parseRawPom(str: String) =
      for {
        xml <- \/.fromEither(compatibility.xmlParse(str))
        _ <- if (xml.label == "project") \/-(()) else -\/("Project definition not found")
        proj <- Pom.project(xml, relocationAsDependency = true)
      } yield proj

    def isArtifact(fileName: String, prefix: String): Option[(String, String)] =
      // TODO There should be a regex for that...
      if (fileName.startsWith(prefix)) {
        val end = fileName.stripPrefix(prefix)
        val idx = end.indexOf('.')
        if (idx >= 0) {
          val ext = end.drop(idx + 1)
          val rem = end.take(idx)
          if (rem.isEmpty)
            Some(("", ext))
          else if (rem.startsWith("-"))
            Some((rem.drop(1), ext))
          else
            None
        } else
          None
      } else
        None


    val projectArtifact0 = projectArtifact(module, version, versioningValue)

    val listFilesUrl = urlFor(moduleVersionPath(module, version)) + "/"

    val changing0 = changing.getOrElse(isSnapshot(version))

    val listFilesArtifact =
      Artifact(
        listFilesUrl,
        Map.empty,
        Map(
          "metadata" -> projectArtifact0
        ),
        Attributes("", ""),
        changing = changing0,
        authentication
      )

    val requiringDirListingProjectArtifact = projectArtifact0
      .copy(
        extra = projectArtifact0.extra + (
          // In LocalUpdate and LocalUpdateChanging mode, makes getting the POM fail if the POM
          // is in cache, but no info about the directory listing is (directory listing not in cache and no kept error
          // for it).
          "required" -> listFilesArtifact
        )
      )

    for {
      str <- fetch(requiringDirListingProjectArtifact)
      rawListFilesPageOpt <- EitherT(F.map(fetch(artifactFor(listFilesUrl, changing0)).run) {
        e => \/-(e.toOption): String \/ Option[String]
      })
      proj0 <- EitherT(F.point[String \/ Project](parseRawPom(str)))
    } yield {

      val foundPublications =
        rawListFilesPageOpt match {
          case Some(rawListFilesPage) =>

            val files = WebPage.listFiles(listFilesUrl, rawListFilesPage)

            val prefix = s"${module.name}-${versioningValue.getOrElse(version)}"

            val packagingTpeMap = proj0.packagingOpt
              .filter(_ != Pom.relocatedPackaging)
              .map { packaging =>
                (MavenSource.typeDefaultClassifier(packaging), MavenSource.typeExtension(packaging)) -> packaging
              }
              .toMap

            files
              .flatMap(isArtifact(_, prefix))
              .map {
                case (classifier, ext) =>
                  val tpe = packagingTpeMap.getOrElse(
                    (classifier, ext),
                    MavenSource.classifierExtensionDefaultTypeOpt(classifier, ext).getOrElse(ext)
                  )
                  val config = MavenSource.typeDefaultConfig(tpe).getOrElse("compile")
                  config -> Publication(
                    module.name,
                    tpe,
                    ext,
                    classifier
                  )
              }

          case None =>
            // Publications can't be listed - MavenSource then handles that
            Nil
        }

      val proj = Pom.addOptionalDependenciesInConfig(
        proj0.copy(configurations = defaultConfigurations),
        Set("", "default"),
        "optional"
      )

      proj.copy(
        actualVersionOpt = Some(version),
        publications = foundPublications
      )
    }
  }

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    Parse.versionInterval(version)
      .orElse(Parse.multiVersionInterval(version))
      .orElse(Parse.ivyLatestSubRevisionInterval(version))
      .filter(_.isValid) match {
        case None =>
          findNoInterval(module, version, fetch).map((source, _))
        case Some(itv) =>
          def v = versions(module, fetch)
          val v0 =
            if (changing.forall(!_) && module.attributes.contains("scalaVersion") && module.attributes.contains("sbtVersion"))
              versionsFromListing(module, fetch).orElse(v)
            else
              v

          v0.flatMap { versions0 =>
            val eitherVersion = {
              val release = Version(versions0.release)

              if (itv.contains(release)) \/-(versions0.release)
              else {
                val inInterval = versions0.available
                  .map(Version(_))
                  .filter(itv.contains)

                if (inInterval.isEmpty) -\/(s"No version found for $version")
                else \/-(inInterval.max.repr)
              }
            }

            eitherVersion match {
              case -\/(reason) => EitherT[F, String, (Artifact.Source, Project)](F.point(-\/(reason)))
              case \/-(version0) =>
                findNoInterval(module, version0, fetch)
                  .map(_.copy(versions = Some(versions0)))
                  .map((source, _))
            }
          }
    }
  }

}
