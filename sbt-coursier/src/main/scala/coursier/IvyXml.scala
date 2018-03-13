package coursier

import java.nio.charset.StandardCharsets.UTF_8

import coursier.internal.FileUtil
import org.apache.ivy.core.module.id.ModuleRevisionId

import scala.collection.JavaConverters._
import scala.xml.{Node, PrefixedAttribute}

import sbt.internal.librarymanagement.IvySbt

object IvyXml {

  def rawContent(
    currentProject: Project,
    shadedConfigOpt: Option[(String, String)]
  ): String = {

    // Important: width = Int.MaxValue, so that no tag gets truncated.
    // In particular, that prevents things like <foo /> to be split to
    // <foo>
    // </foo>
    // by the pretty-printer.
    // See https://github.com/sbt/sbt/issues/3412.
    val printer = new scala.xml.PrettyPrinter(Int.MaxValue, 2)

    """<?xml version="1.0" encoding="UTF-8"?>""" + '\n' +
      printer.format(content(currentProject, shadedConfigOpt.map(_._2)))
  }

  // These are required for publish to be fine, later on.
  def writeFiles(
    currentProject: Project,
    shadedConfigOpt: Option[(String, String)],
    ivySbt: IvySbt,
    log: sbt.Logger
  ): Unit = {

    val ivyCacheManager = ivySbt.withIvy(log)(ivy =>
      ivy.getResolutionCacheManager
    )

    val ivyModule = ModuleRevisionId.newInstance(
      currentProject.module.organization,
      currentProject.module.name,
      currentProject.version,
      currentProject.module.attributes.asJava
    )

    val cacheIvyFile = ivyCacheManager.getResolvedIvyFileInCache(ivyModule)
    val cacheIvyPropertiesFile = ivyCacheManager.getResolvedIvyPropertiesInCache(ivyModule)

    val content0 = rawContent(currentProject, shadedConfigOpt)
    cacheIvyFile.getParentFile.mkdirs()
    log.info(s"Writing Ivy file $cacheIvyFile")
    FileUtil.write(cacheIvyFile, content0.getBytes(UTF_8))

    // Just writing an empty file here... Are these only used?
    cacheIvyPropertiesFile.getParentFile.mkdirs()
    FileUtil.write(cacheIvyPropertiesFile, Array())
  }

  def content(project0: Project, shadedConfigOpt: Option[String]): Node = {

    val filterOutDependencies =
      shadedConfigOpt.toSet[String].flatMap { shadedConfig =>
        project0
          .dependencies
          .collect { case (`shadedConfig`, dep) => dep }
      }

    val project: Project = project0.copy(
      dependencies = project0.dependencies.collect {
        case p @ (_, dep) if !filterOutDependencies(dep) => p
      }
    )

    val infoAttrs = project.module.attributes.foldLeft[xml.MetaData](xml.Null) {
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
        organisation={project.module.organization}
        module={project.module.name}
        revision={project.version}
      >
        {licenseElems}
        <description>{project.info.description}</description>
      </info>
    } % infoAttrs

    val confElems = project.configurations.toVector.collect {
      case (name, extends0) if shadedConfigOpt != Some(name) =>
        val extends1 = shadedConfigOpt.fold(extends0)(c => extends0.filter(_ != c))
        val n = <conf name={name} visibility="public" description="" />
        if (extends1.nonEmpty)
          n % <x extends={extends1.mkString(",")} />.attributes
        else
          n
    }

    val publications = project
      .publications
      .groupBy { case (_, p) => p }
      .mapValues { _.map { case (cfg, _) => cfg } }

    val publicationElems = publications.map {
      case (pub, configs) =>
        val n = <artifact name={pub.name} type={pub.`type`} ext={pub.ext} conf={configs.mkString(",")} />

        if (pub.classifier.nonEmpty)
          n % <x e:classifier={pub.classifier} />.attributes
        else
          n
    }

    val dependencyElems = project.dependencies.toVector.map {
      case (conf, dep) =>
        val excludes = dep.exclusions.toSeq.map {
          case (org, name) =>
            <exclude org={org} module={name} name="*" type="*" ext="*" conf="" matcher="exact"/>
        }

        val n = <dependency org={dep.module.organization} name={dep.module.name} rev={dep.version} conf={s"$conf->${dep.configuration}"}>
          {excludes}
        </dependency>

        val moduleAttrs = dep.module.attributes.foldLeft[xml.MetaData](xml.Null) {
          case (acc, (k, v)) =>
            new PrefixedAttribute("e", k, v, acc)
        }

        n % moduleAttrs
    }

    <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
      {infoElem}
      <configurations>{confElems}</configurations>
      <publications>{publicationElems}</publications>
      <dependencies>{dependencyElems}</dependencies>
    </ivy-module>
  }

}
