package lmcoursier.definitions

import lmcoursier.credentials.{Credentials, DirectCredentials, FileCredentials}
import sbt.librarymanagement.InclExclRule

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
    coursier.core.Authentication(authentication.user, authentication.password)
      .withOptional(authentication.optional)
      .withRealmOpt(authentication.realmOpt)
      .withHttpHeaders(authentication.headers)
      .withHttpsOnly(authentication.httpsOnly)
      .withPassOnRedirect(authentication.passOnRedirect)

  def module(mod: Module): coursier.core.Module =
    module(mod.organization.value, mod.name.value, mod.attributes)

  def module(organization: String, name: String, attributes: Map[String, String] = Map.empty): coursier.core.Module =
    coursier.core.Module(
      coursier.core.Organization(organization),
      coursier.core.ModuleName(name),
      attributes
    )

  def moduleMatchers(matcher: ModuleMatchers): coursier.util.ModuleMatchers =
    coursier.util.ModuleMatchers(
      exclude = matcher.exclude map { x =>
        coursier.util.ModuleMatcher(module(x))
      },
      include = matcher.include map { x =>
        coursier.util.ModuleMatcher(module(x))
      },
      includeByDefault = matcher.includeByDefault
    )

  def reconciliation(r: Reconciliation): coursier.core.Reconciliation =
    r match {
      case Reconciliation.Default => coursier.core.Reconciliation.Default
      case Reconciliation.Relaxed => coursier.core.Reconciliation.Relaxed
      case Reconciliation.Strict => coursier.core.Reconciliation.Strict
      case Reconciliation.SemVer => coursier.core.Reconciliation.SemVer
    }

  def reconciliation(rs: Vector[(ModuleMatchers, Reconciliation)]):
    Vector[(coursier.util.ModuleMatchers, coursier.core.Reconciliation)] =
    rs map { case (m, r) => (moduleMatchers(m), reconciliation(r)) }

  def sameVersions(sv: Seq[Set[InclExclRule]]):
    Seq[(coursier.params.rule.SameVersion, coursier.params.rule.RuleResolution)] =
    sv.map { libs =>
      val matchers = libs.map(rule => coursier.util.ModuleMatcher(module(rule.organization, rule.name)))
      coursier.params.rule.SameVersion(matchers) -> coursier.params.rule.RuleResolution.TryResolve
    }

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
        },
        None // TODO Add scm field in lmcoursier.definitions.Info?
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

  def strict(strict: Strict): coursier.params.rule.Strict =
    coursier.params.rule.Strict()
      .withInclude(strict.include.map {
        case (o, n) =>
          coursier.util.ModuleMatcher(coursier.Module(coursier.Organization(o), coursier.ModuleName(n)))
      })
      .withExclude(strict.exclude.map {
        case (o, n) =>
          coursier.util.ModuleMatcher(coursier.Module(coursier.Organization(o), coursier.ModuleName(n)))
      })
      .withIncludeByDefault(strict.includeByDefault)
      .withIgnoreIfForcedVersion(strict.ignoreIfForcedVersion)
      .withSemVer(strict.semVer)

  def cachePolicy(r: CachePolicy): coursier.cache.CachePolicy =
    r match {
      case CachePolicy.LocalOnly => coursier.cache.CachePolicy.LocalOnly
      case CachePolicy.LocalOnlyIfValid => coursier.cache.CachePolicy.LocalOnlyIfValid
      case CachePolicy.LocalUpdateChanging => coursier.cache.CachePolicy.LocalUpdateChanging
      case CachePolicy.LocalUpdate => coursier.cache.CachePolicy.LocalUpdate
      case CachePolicy.UpdateChanging => coursier.cache.CachePolicy.UpdateChanging
      case CachePolicy.Update => coursier.cache.CachePolicy.Update
      case CachePolicy.FetchMissing => coursier.cache.CachePolicy.FetchMissing
      case CachePolicy.ForceDownload => coursier.cache.CachePolicy.ForceDownload
    }
}
