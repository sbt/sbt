/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package librarymanagement

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

import lmcoursier.definitions.{ Configuration, Project }
import org.apache.ivy.core.module.id.ModuleRevisionId
import Def.Setting
import sbt.Keys.{
  csrProject,
  csrPublications,
  publishLocalConfiguration,
  publishConfiguration,
  useCoursier
}
import sbt.librarymanagement.PublishConfiguration
import scala.collection.JavaConverters._
import scala.xml.{ Node, PrefixedAttribute }

object IvyXml {
  import sbt.Project._

  private def rawContent(
      currentProject: Project,
      shadedConfigOpt: Option[Configuration]
  ): String = {

    // Important: width = Int.MaxValue, so that no tag gets truncated.
    // In particular, that prevents things like <foo /> to be split to
    // <foo>
    // </foo>
    // by the pretty-printer.
    // See https://github.com/sbt/sbt/issues/3412.
    val printer = new scala.xml.PrettyPrinter(Int.MaxValue, 2)

    """<?xml version="1.0" encoding="UTF-8"?>""" + '\n' +
      printer.format(content(currentProject, shadedConfigOpt))
  }

  // These are required for publish to be fine, later on.
  private def writeFiles(
      currentProject: Project,
      shadedConfigOpt: Option[Configuration],
      ivySbt: IvySbt,
      log: sbt.util.Logger
  ): Unit = {

    val ivyCacheManager = ivySbt.withIvy(log)(ivy => ivy.getResolutionCacheManager)

    val ivyModule = ModuleRevisionId.newInstance(
      currentProject.module.organization.value,
      currentProject.module.name.value,
      currentProject.version,
      currentProject.module.attributes.asJava
    )

    val cacheIvyFile = ivyCacheManager.getResolvedIvyFileInCache(ivyModule)
    val cacheIvyPropertiesFile = ivyCacheManager.getResolvedIvyPropertiesInCache(ivyModule)

    val content0 = rawContent(currentProject, shadedConfigOpt)
    cacheIvyFile.getParentFile.mkdirs()
    log.debug(s"writing Ivy file $cacheIvyFile")
    Files.write(cacheIvyFile.toPath, content0.getBytes(UTF_8))

    // Just writing an empty file here... Are these only used?
    cacheIvyPropertiesFile.getParentFile.mkdirs()
    Files.write(cacheIvyPropertiesFile.toPath, Array.emptyByteArray)
    ()
  }

  private def content(project0: Project, shadedConfigOpt: Option[Configuration]): Node = {

    val filterOutDependencies =
      shadedConfigOpt.toSet[Configuration].flatMap { shadedConfig =>
        project0.dependencies
          .collect { case (conf, dep) if conf.value == shadedConfig.value => dep }
      }

    val project: Project = project0.withDependencies(project0.dependencies.collect {
      case p @ (_, dep) if !filterOutDependencies(dep) => p
    })

    val infoAttrs =
      (project.module.attributes.toSeq ++ project.properties).foldLeft[xml.MetaData](xml.Null) {
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

    val descriptionElem = {
      val n = <description>{project.info.description}</description>
      if (project.info.homePage.nonEmpty)
        n % <x homepage={project.info.homePage} />.attributes
      else
        n
    }

    val infoElem = {
      <info
        organisation={project.module.organization.value}
        module={project.module.name.value}
        revision={project.version}
      >
        {licenseElems}
        {descriptionElem}
      </info>
    } % infoAttrs

    val confElems = project.configurations.toVector.collect {
      case (name, extends0) if !shadedConfigOpt.exists(_.value == name.value) =>
        val extends1 = shadedConfigOpt.fold(extends0)(c => extends0.filter(_.value != c.value))
        val n = <conf name={name.value} visibility="public" description="" />
        if (extends1.nonEmpty)
          n % <x extends={extends1.map(_.value).mkString(",")} />.attributes
        else
          n
    }

    val publications = project.publications
      .groupBy { case (_, p)             => p }
      .mapValues { _.map { case (cfg, _) => cfg } }

    val publicationElems = publications.map {
      case (pub, configs) =>
        val n =
          <artifact name={pub.name} type={pub.`type`.value} ext={pub.ext.value} conf={
            configs.map(_.value).mkString(",")
          } />

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

        val n =
          <dependency org={dep.module.organization.value} name={dep.module.name.value} rev={
            dep.version
          } conf={s"${conf.value}->${dep.configuration.value}"}>
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

  private def makeIvyXmlBefore[T](
      task: TaskKey[T],
      shadedConfigOpt: Option[Configuration]
  ): Setting[Task[T]] =
    task := task.dependsOn {
      Def.taskDyn {
        val doGen = useCoursier.value
        if (doGen)
          Def.task {
            val currentProject = {
              val proj = csrProject.value
              val publications = csrPublications.value
              proj.withPublications(publications)
            }
            IvyXml.writeFiles(
              currentProject,
              shadedConfigOpt,
              sbt.Keys.ivySbt.value,
              sbt.Keys.streams.value.log
            )
          } else
          Def.task(())
      }
    }.value

  private lazy val needsIvyXmlLocal = Seq(publishLocalConfiguration) ++ getPubConf(
    "makeIvyXmlLocalConfiguration"
  )
  private lazy val needsIvyXml = Seq(publishConfiguration) ++ getPubConf(
    "makeIvyXmlConfiguration"
  )

  private[this] def getPubConf(method: String): List[TaskKey[PublishConfiguration]] =
    try {
      val cls = sbt.Keys.getClass
      val m = cls.getMethod(method)
      val task = m.invoke(sbt.Keys).asInstanceOf[TaskKey[PublishConfiguration]]
      List(task)
    } catch {
      case _: Throwable => // FIXME Too wide
        Nil
    }

  def generateIvyXmlSettings(
      shadedConfigOpt: Option[Configuration] = None
  ): Seq[Setting[_]] =
    (needsIvyXml ++ needsIvyXmlLocal).map(makeIvyXmlBefore(_, shadedConfigOpt))

}
