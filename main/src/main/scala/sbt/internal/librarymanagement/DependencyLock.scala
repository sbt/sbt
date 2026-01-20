/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import java.security.MessageDigest
import sbt.librarymanagement.ModuleID
import sjsonnew.*

final case class LockedArtifact(
    classifier: Option[String],
    extension: String,
    url: String,
    sha256: Option[String]
)

final case class LockedDependency(
    organization: String,
    name: String,
    version: String,
    configurations: Option[String],
    artifacts: Vector[LockedArtifact]
)

final case class LockedResolver(
    name: String,
    root: String
)

final case class ProjectLock(
    projectId: String,
    dependencies: Vector[LockedDependency]
)

final case class DependencyLockFile(
    lockVersion: String,
    sbtVersion: String,
    buildClock: String,
    resolvers: Vector[LockedResolver],
    projects: Vector[ProjectLock]
):
  def isValid(currentBuildClock: String): Boolean =
    buildClock == currentBuildClock

object DependencyLockFile:
  val CurrentLockVersion = "1.0"
  val LockFileName = "build.sbt.lock"

  def computeBuildClock(
      libraryDependencies: Seq[ModuleID],
      resolvers: Seq[String]
  ): String =
    val digest = MessageDigest.getInstance("SHA-256")
    val sortedDeps = libraryDependencies
      .map(m => s"${m.organization}:${m.name}:${m.revision}")
      .sorted
    sortedDeps.foreach(d => digest.update(d.getBytes("UTF-8")))
    resolvers.sorted.foreach(r => digest.update(r.getBytes("UTF-8")))
    digest.digest().map("%02x".format(_)).mkString

  def lockFilePath(baseDirectory: File): File =
    new File(baseDirectory, LockFileName)

trait LockedArtifactFormats:
  self: sjsonnew.BasicJsonProtocol =>

  given LockedArtifactFormat: JsonFormat[LockedArtifact] =
    new JsonFormat[LockedArtifact]:
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): LockedArtifact =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val classifier = unbuilder.readField[Option[String]]("classifier")
            val extension = unbuilder.readField[String]("extension")
            val url = unbuilder.readField[String]("url")
            val sha256 = unbuilder.readField[Option[String]]("sha256")
            unbuilder.endObject()
            LockedArtifact(classifier, extension, url, sha256)
          case None =>
            deserializationError("Expected JsObject but found None")

      override def write[J](obj: LockedArtifact, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addField("classifier", obj.classifier)
        builder.addField("extension", obj.extension)
        builder.addField("url", obj.url)
        builder.addField("sha256", obj.sha256)
        builder.endObject()

trait LockedDependencyFormats:
  self: sjsonnew.BasicJsonProtocol & LockedArtifactFormats =>

  given LockedDependencyFormat: JsonFormat[LockedDependency] =
    new JsonFormat[LockedDependency]:
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): LockedDependency =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val organization = unbuilder.readField[String]("organization")
            val name = unbuilder.readField[String]("name")
            val version = unbuilder.readField[String]("version")
            val configurations = unbuilder.readField[Option[String]]("configurations")
            val artifacts = unbuilder.readField[Vector[LockedArtifact]]("artifacts")
            unbuilder.endObject()
            LockedDependency(organization, name, version, configurations, artifacts)
          case None =>
            deserializationError("Expected JsObject but found None")

      override def write[J](obj: LockedDependency, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addField("organization", obj.organization)
        builder.addField("name", obj.name)
        builder.addField("version", obj.version)
        builder.addField("configurations", obj.configurations)
        builder.addField("artifacts", obj.artifacts)
        builder.endObject()

trait LockedResolverFormats:
  self: sjsonnew.BasicJsonProtocol =>

  given LockedResolverFormat: JsonFormat[LockedResolver] =
    new JsonFormat[LockedResolver]:
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): LockedResolver =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val name = unbuilder.readField[String]("name")
            val root = unbuilder.readField[String]("root")
            unbuilder.endObject()
            LockedResolver(name, root)
          case None =>
            deserializationError("Expected JsObject but found None")

      override def write[J](obj: LockedResolver, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addField("name", obj.name)
        builder.addField("root", obj.root)
        builder.endObject()

trait ProjectLockFormats:
  self: sjsonnew.BasicJsonProtocol & LockedArtifactFormats & LockedDependencyFormats =>

  given ProjectLockFormat: JsonFormat[ProjectLock] =
    new JsonFormat[ProjectLock]:
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): ProjectLock =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val projectId = unbuilder.readField[String]("projectId")
            val dependencies = unbuilder.readField[Vector[LockedDependency]]("dependencies")
            unbuilder.endObject()
            ProjectLock(projectId, dependencies)
          case None =>
            deserializationError("Expected JsObject but found None")

      override def write[J](obj: ProjectLock, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addField("projectId", obj.projectId)
        builder.addField("dependencies", obj.dependencies)
        builder.endObject()

trait DependencyLockFileFormats:
  self: sjsonnew.BasicJsonProtocol & LockedArtifactFormats & LockedDependencyFormats &
    LockedResolverFormats & ProjectLockFormats =>

  given DependencyLockFileFormat: JsonFormat[DependencyLockFile] =
    new JsonFormat[DependencyLockFile]:
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): DependencyLockFile =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val lockVersion = unbuilder.readField[String]("lockVersion")
            val sbtVersion = unbuilder.readField[String]("sbtVersion")
            val buildClock = unbuilder.readField[String]("buildClock")
            val resolvers = unbuilder.readField[Vector[LockedResolver]]("resolvers")
            val projects = unbuilder.readField[Vector[ProjectLock]]("projects")
            unbuilder.endObject()
            DependencyLockFile(lockVersion, sbtVersion, buildClock, resolvers, projects)
          case None =>
            deserializationError("Expected JsObject but found None")

      override def write[J](obj: DependencyLockFile, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addField("lockVersion", obj.lockVersion)
        builder.addField("sbtVersion", obj.sbtVersion)
        builder.addField("buildClock", obj.buildClock)
        builder.addField("resolvers", obj.resolvers)
        builder.addField("projects", obj.projects)
        builder.endObject()

object DependencyLockCodec
    extends sjsonnew.BasicJsonProtocol
    with LockedArtifactFormats
    with LockedDependencyFormats
    with LockedResolverFormats
    with ProjectLockFormats
    with DependencyLockFileFormats
