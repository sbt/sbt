package lmcoursier.definitions

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
      attributes(dependency.attributes),
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

}
