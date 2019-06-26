package lmcoursier.definitions

import lmcoursier.credentials.{Credentials, DirectCredentials, FileCredentials}

// TODO Make private[lmcoursier]
// private[coursier]
object ToCoursier {

  def configuration(configuration: Configuration): coursier.core.Configuration =
    coursier.core.Configuration(configuration.value)

  private def attributes(attributes: Attributes): coursier.core.Attributes =
    coursier.core.Attributes(
      coursier.core.Type(attributes.`type`.value),
      coursier.core.Classifier(attributes.classifier.value)
    )

  def publication(publication: Publication): coursier.core.Publication =
    coursier.core.Publication(
      publication.name,
      coursier.core.Type(publication.`type`.value),
      coursier.core.Extension(publication.ext.value),
      coursier.core.Classifier(publication.classifier.value)
    )

  def authentication(authentication: Authentication): coursier.core.Authentication =
    coursier.core.Authentication(
      authentication.user,
      authentication.password,
      authentication.optional,
      authentication.realmOpt
    )

  def module(module: Module): coursier.core.Module =
    coursier.core.Module(
      coursier.core.Organization(module.organization.value),
      coursier.core.ModuleName(module.name.value),
      module.attributes
    )

  def dependency(dependency: Dependency): coursier.core.Dependency =
    coursier.core.Dependency(
      module(dependency.module),
      dependency.version,
      configuration(dependency.configuration),
      dependency.exclusions.map {
        case (org, name) =>
          (coursier.core.Organization(org.value), coursier.core.ModuleName(name.value))
      },
      publication(dependency.publication),
      dependency.optional,
      dependency.transitive
    )

  def project(project: Project): coursier.core.Project =
    coursier.core.Project(
      module(project.module),
      project.version,
      project.dependencies.map {
        case (conf, dep) =>
          configuration(conf) -> dependency(dep)
      },
      project.configurations.map {
        case (k, l) =>
          configuration(k) -> l.map(configuration)
      },
      None,
      Nil,
      project.properties,
      Nil,
      None,
      None,
      project.packagingOpt.map(t => coursier.core.Type(t.value)),
      relocated = false,
      None,
      project.publications.map {
        case (conf, pub) =>
          configuration(conf) -> publication(pub)
      },
      coursier.core.Info(
        project.info.description,
        project.info.homePage,
        project.info.licenses,
        project.info.developers.map { dev =>
          coursier.core.Info.Developer(
            dev.id,
            dev.name,
            dev.url
          )
        },
        project.info.publication.map { dt =>
          coursier.core.Versions.DateTime(
            dt.year,
            dt.month,
            dt.day,
            dt.hour,
            dt.minute,
            dt.second
          )
        }
      )
    )

  def credentials(credentials: Credentials): coursier.credentials.Credentials =
    credentials match {
      case d: DirectCredentials =>
        coursier.credentials.DirectCredentials()
          .withHost(d.host)
          .withUsername(d.username)
          .withPassword(d.password)
          .withRealm(d.realm)
          .withOptional(d.optional)
          .withMatchHost(d.matchHost)
          .withHttpsOnly(d.httpsOnly)
      case f: FileCredentials =>
        coursier.credentials.FileCredentials(f.path)
          .withOptional(f.optional)
    }

  def cacheLogger(logger: CacheLogger): coursier.cache.CacheLogger =
    new coursier.cache.CacheLogger {
      override def foundLocally(url: String): Unit =
        logger.foundLocally(url)
      override def downloadingArtifact(url: String): Unit =
        logger.downloadingArtifact(url)
      override def downloadProgress(url: String, downloaded: Long): Unit =
        logger.downloadProgress(url, downloaded)
      override def downloadedArtifact(url: String, success: Boolean): Unit =
        logger.downloadedArtifact(url, success)
      override def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit =
        logger.checkingUpdates(url, currentTimeOpt)
      override def checkingUpdatesResult(url: String, currentTimeOpt: Option[Long], remoteTimeOpt: Option[Long]): Unit =
        logger.checkingUpdatesResult(url, currentTimeOpt, remoteTimeOpt)
      override def downloadLength(url: String, totalLength: Long, alreadyDownloaded: Long, watching: Boolean): Unit =
        logger.downloadLength(url, totalLength, alreadyDownloaded, watching)
      override def gettingLength(url: String): Unit =
        logger.gettingLength(url)
      override def gettingLengthResult(url: String, length: Option[Long]): Unit =
        logger.gettingLengthResult(url, length)
      override def removedCorruptFile(url: String, reason: Option[String]): Unit =
        logger.removedCorruptFile(url, reason)
      override def init(sizeHint: Option[Int] = None): Unit =
        logger.init(sizeHint)
      override def stop(): Unit =
        logger.stop()
    }

}
