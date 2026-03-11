/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import java.io.File
import org.apache.ivy.core.module.descriptor.{ DependencyDescriptor, ModuleDescriptor }
import org.apache.ivy.core.module.id.ModuleRevisionId
import sbt.Def.{ Initialize, Setting }
import sbt.Keys.*
import sbt.ProjectExtra.*
import sbt.internal.LibraryManagement
import sbt.internal.librarymanagement.{ IvyActions, IvySbt, IvyXml, ProjectResolver }
import sbt.internal.librarymanagement.ivy.*
import sbt.io.syntax.*
import sbt.librarymanagement.*
import sbt.std.TaskExtra.*
import lmcoursier.definitions.{
  Classifier as CClassifier,
  Configuration as CConfiguration,
  Dependency as CDependency,
  Extension as CExtension,
  Info as CInfo,
  Module as CModule,
  ModuleName as CModuleName,
  Organization as COrganization,
  Project as CProject,
  Publication as CPublication,
  Type as CType,
}
import lmcoursier.Inputs
import scala.jdk.CollectionConverters.*

/**
 * AutoPlugin that provides all Ivy-specific functionality.
 * This plugin overrides the stub defaults in main/ with real Ivy implementations.
 */
object IvyDependencyPlugin extends AutoPlugin:
  override def requires = IvyPlugin
  override def trigger = allRequirements

  override lazy val globalSettings: Seq[Setting[?]] = Seq(
    updateOptions := UpdateOptions(),
  )

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    ivyConfiguration := Def.uncached(
      Def
        .ifS(Def.task { useIvy.value })(
          Def.task { mkIvyConfiguration.value: Any }
        )(
          Def.task { (): Any }
        )
        .value
    ),
    publisher := Def.uncached(
      Def
        .ifS(Def.task { useIvy.value })(
          Def.task {
            IvyPublisher(ivyConfiguration.value.asInstanceOf[IvyConfiguration])
          }
        )(
          Def.task {
            Classpaths.defaultPublisher(dependencyResolution.value, fullResolvers.value.toVector)
          }
        )
        .value
    ),
    ivySbt := Def.uncached(
      Def
        .ifS(Def.task { useIvy.value })(
          Def.task { ivySbt0.value: Any }
        )(
          Def.task { (): Any }
        )
        .value
    ),
    ivyModule := Def.uncached(
      Def
        .ifS(Def.task { useIvy.value })(
          Def.task {
            val is = ivySbt.value.asInstanceOf[IvySbt]
            new is.Module(moduleSettings.value): Any
          }
        )(
          Def.task { (): Any }
        )
        .value
    ),
    projectResolver := Def.uncached(
      Def
        .ifS(Def.task { useIvy.value })(
          projectResolverTask
        )(
          Classpaths.projectResolverTask
        )
        .value
    ),
    csrExtraProjects := Def.uncached(
      Def
        .ifS(Def.task { useIvy.value })(
          coursierExtraProjectsTask
        )(
          Def.task { Nil }
        )
        .value
    ),
  ) ++ IvyXml.generateIvyXmlSettings() ++ ivyPublishOrSkipSettings

  private lazy val ivySbt0: Initialize[Task[IvySbt]] =
    Def.task {
      IvyCredentials.register(credentials.value, streams.value.log)
      new IvySbt(ivyConfiguration.value.asInstanceOf[IvyConfiguration])
    }

  private lazy val mkIvyConfiguration: Initialize[Task[InlineIvyConfiguration]] =
    Def.task {
      val (rs, other) = (fullResolvers.value.toVector, otherResolvers.value.toVector)
      val s = streams.value
      Classpaths.warnResolversConflict(rs ++: other, s.log)
      Classpaths.errorInsecureProtocol(rs ++: other, s.log)
      InlineIvyConfiguration()
        .withPaths(ivyPaths.value)
        .withResolvers(rs)
        .withOtherResolvers(other)
        .withModuleConfigurations(moduleConfigurations.value.toVector)
        .withLock(LibraryManagement.lock(appConfiguration.value))
        .withChecksums((update / checksums).value.toVector)
        .withResolutionCacheDir(target.value / "resolution-cache")
        .withUpdateOptions(updateOptions.value.asInstanceOf[UpdateOptions])
        .withLog(s.log)
    }

  private def depMap: Initialize[Task[Map[ModuleRevisionId, ModuleDescriptor]]] =
    import sbt.TupleSyntax.*
    (buildDependencies.toTaskable, thisProjectRef.toTaskable, settingsData, streams)
      .flatMapN { (bd, thisProj, data, s) =>
        depMap(bd.classpathTransitiveRefs(thisProj), data, s.log)
      }

  private def depMap(
      projects: Seq[ProjectRef],
      data: Def.Settings,
      log: sbt.util.Logger
  ): Task[Map[ModuleRevisionId, ModuleDescriptor]] =
    val ivyModules = projects.flatMap { proj =>
      (proj / ivyModule).get(data)
    }.join
    ivyModules.mapN { mod =>
      mod.map { m => m.asInstanceOf[IvySbt#Module].dependencyMapping(log) }.toMap
    }

  private def projectResolverTask: Initialize[Task[Resolver]] =
    depMap.map { m =>
      val resolver = new ProjectResolver(ProjectResolver.InterProject, m)
      new RawRepository(resolver, resolver.getName)
    }

  private def ivyPublishOrSkipSettings: Seq[Setting[?]] =
    Seq(
      deliver := deliverTask(makeIvyXmlConfiguration).value,
      deliverLocal := deliverTask(makeIvyXmlLocalConfiguration).value,
      makeIvyXml := deliverTask(makeIvyXmlConfiguration).value,
    )

  private def deliverTask(config: TaskKey[PublishConfiguration]): Initialize[Task[File]] =
    Def.task {
      Def.unit(update.value)
      if !useIvy.value then sys.error("deliver/makeIvyXml requires useIvy := true")
      IvyActions.deliver(
        ivyModule.value.asInstanceOf[IvySbt#Module],
        config.value,
        streams.value.log
      )
    }

  private lazy val coursierExtraProjectsTask: Initialize[Task[Seq[CProject]]] =
    Def.task {
      val projects = csrInterProjectDependencies.value
      val projectModules = projects.map(_.module).toSet
      depMap.value
        .map { (id, desc) =>
          moduleFromIvy(id) -> desc
        }
        .filter { case (module, _) =>
          !projectModules(module)
        }
        .toVector
        .map { (module, v) =>
          val configurations = v.getConfigurations.map { c =>
            CConfiguration(c.getName) -> c.getExtends.map(CConfiguration(_)).toSeq
          }.toMap
          val deps = v.getDependencies.flatMap(dependencyFromIvy)
          CProject(
            module,
            v.getModuleRevisionId.getRevision,
            deps.toSeq,
            configurations,
            Nil,
            None,
            Nil,
            CInfo("", "", Nil, Nil, None)
          )
        }
    }

  private def moduleFromIvy(id: ModuleRevisionId): CModule =
    CModule(
      COrganization(id.getOrganisation),
      CModuleName(id.getName),
      id.getExtraAttributes.asScala.map { (k0, v0) =>
        k0.asInstanceOf[String] -> v0.asInstanceOf[String]
      }.toMap
    )

  private def dependencyFromIvy(
      desc: DependencyDescriptor
  ): Seq[(CConfiguration, CDependency)] =
    val id = desc.getDependencyRevisionId
    val module = moduleFromIvy(id)
    val exclusions = desc.getAllExcludeRules.map { rule =>
      val modId = rule.getId.getModuleId
      (COrganization(modId.getOrganisation), CModuleName(modId.getName))
    }.toSet
    val configurations = desc.getModuleConfigurations.toVector
      .flatMap(Inputs.ivyXmlMappings)

    def dependency(conf: CConfiguration, pub: CPublication) = CDependency(
      module,
      id.getRevision,
      conf,
      exclusions,
      pub,
      optional = false,
      desc.isTransitive
    )

    val publications: CConfiguration => CPublication =
      val artifacts = desc.getAllDependencyArtifacts
      val m = artifacts.toVector.flatMap { art =>
        val pub = CPublication(
          art.getName,
          CType(art.getType),
          CExtension(art.getExt()),
          CClassifier("")
        )
        art.getConfigurations.map(CConfiguration(_)).toVector.map { conf =>
          conf -> pub
        }
      }.toMap
      c => m.getOrElse(c, CPublication("", CType(""), CExtension(""), CClassifier("")))

    configurations.map { (from, to) =>
      from -> dependency(to, publications(to))
    }
end IvyDependencyPlugin
