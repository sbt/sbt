package lmcoursier

import lmcoursier.definitions.{Configuration, Project}

import scala.xml.{Node, PrefixedAttribute}

object IvyXml {

  @deprecated("Use the override accepting 3 arguments", "2.0.0-RC6-6")
  def apply(
    currentProject: Project,
    exclusions: Seq[(String, String)]
  ): String =
    apply(currentProject, exclusions, Nil)

  def apply(
    currentProject: Project,
    exclusions: Seq[(String, String)],
    overrides: Seq[(String, String, String)]
  ): String = {

    // Important: width = Int.MaxValue, so that no tag gets truncated.
    // In particular, that prevents things like <foo /> to be split to
    // <foo>
    // </foo>
    // by the pretty-printer.
    // See https://github.com/sbt/sbt/issues/3412.
    val printer = new scala.xml.PrettyPrinter(Int.MaxValue, 2)

    """<?xml version="1.0" encoding="UTF-8"?>""" + '\n' +
      printer.format(content(currentProject, exclusions, overrides))
  }

  // These are required for publish to be fine, later on.
  private def content(
    project: Project,
    exclusions: Seq[(String, String)],
    overrides: Seq[(String, String, String)]
  ): Node = {

    val props = project.module.attributes.toSeq ++ project.properties
    val infoAttrs = props.foldLeft[xml.MetaData](xml.Null) {
      case (acc, (k, v)) =>
        new PrefixedAttribute("e", k, v, acc)
    }

    val licenseElems = project.info.licenses.map {
      case (name, urlOpt) =>
        val n = <license name={name} />

        urlOpt.fold(n) { url =>
          n % <x url={url} />.attributes
        }
    }

    val infoElem = {
      <info
        organisation={project.module.organization.value}
        module={project.module.name.value}
        revision={project.version}
      >
        {licenseElems}
        <description>{project.info.description}</description>
      </info>
    } % infoAttrs

    val confElems = project.configurations.toVector.collect {
      case (name, extends0) =>
        val n = <conf name={name.value} visibility="public" description="" />
        if (extends0.nonEmpty)
          n % <x extends={extends0.map(_.value).mkString(",")} />.attributes
        else
          n
    }

    val publications = project
      .publications
      .groupBy { case (_, p) => p }
      .mapValues { _.map { case (cfg, _) => cfg } }

    val publicationElems = publications.map {
      case (pub, configs) =>
        val n = <artifact name={pub.name} type={pub.`type`.value} ext={pub.ext.value} conf={configs.map(_.value).mkString(",")} />

        if (pub.classifier.value.nonEmpty)
          n % <x e:classifier={pub.classifier.value} />.attributes
        else
          n
    }

    val dependencyElems = project.dependencies.toVector.map {
      case (conf, dep) =>
        val excludes = dep.exclusions.toSeq.map {
          case (org, name) =>
            <exclude org={org.value} module={name.value} name="*" type="*" ext="*" conf="" matcher="exact"/>
        }

        val n = <dependency org={dep.module.organization.value} name={dep.module.name.value} rev={dep.version} conf={s"${conf.value}->${dep.configuration.value}"}>
          {excludes}
        </dependency>

        val moduleAttrs = dep.module.attributes.foldLeft[xml.MetaData](xml.Null) {
          case (acc, (k, v)) =>
            new PrefixedAttribute("e", k, v, acc)
        }

        n % moduleAttrs
    }

    val excludeElems = exclusions.toVector.map {
      case (org, name) =>
        <exclude org={org} module={name} artifact="*" type="*" ext="*" matcher="exact"/>
    }

    val overrideElems = overrides.toVector.map {
      case (org, name, ver) =>
        <override org={org} module={name} rev={ver} matcher="exact"/>
    }

    <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
      {infoElem}
      <configurations>{confElems}</configurations>
      <publications>{publicationElems}</publications>
      <dependencies>{dependencyElems}{excludeElems}{overrideElems}</dependencies>
    </ivy-module>
  }

}
