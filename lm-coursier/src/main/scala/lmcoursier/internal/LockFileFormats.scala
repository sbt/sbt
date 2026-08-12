package lmcoursier.internal

import sjsonnew.*

trait ArtifactLockFormats { self: sjsonnew.BasicJsonProtocol =>
  given ArtifactLockFormat: JsonFormat[ArtifactLock] = new JsonFormat[ArtifactLock] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): ArtifactLock =
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val url = unbuilder.readField[String]("url")
          val classifier = unbuilder.readField[Option[String]]("classifier")
          val extension = unbuilder.readField[String]("extension")
          val tpe = unbuilder.readField[String]("tpe")
          unbuilder.endObject()
          ArtifactLock(url, classifier, extension, tpe)
        case None =>
          deserializationError("Expected JsObject but found None")
      }

    override def write[J](obj: ArtifactLock, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("url", obj.url)
      builder.addField("classifier", obj.classifier)
      builder.addField("extension", obj.extension)
      builder.addField("tpe", obj.tpe)
      builder.endObject()
    }
  }
}

trait DependencyLockFormats { self: sjsonnew.BasicJsonProtocol & ArtifactLockFormats =>
  given DependencyLockFormat: JsonFormat[DependencyLock] = new JsonFormat[DependencyLock] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): DependencyLock =
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val organization = unbuilder.readField[String]("organization")
          val name = unbuilder.readField[String]("name")
          val version = unbuilder.readField[String]("version")
          val configuration = unbuilder.readField[String]("configuration")
          val classifier = unbuilder.readField[Option[String]]("classifier")
          val tpe = unbuilder.readField[String]("tpe")
          val transitives = unbuilder.readField[Vector[String]]("transitives")
          val artifacts = unbuilder.readField[Vector[ArtifactLock]]("artifacts")
          unbuilder.endObject()
          DependencyLock(
            organization,
            name,
            version,
            configuration,
            classifier,
            tpe,
            transitives,
            artifacts
          )
        case None =>
          deserializationError("Expected JsObject but found None")
      }

    override def write[J](obj: DependencyLock, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("organization", obj.organization)
      builder.addField("name", obj.name)
      builder.addField("version", obj.version)
      builder.addField("configuration", obj.configuration)
      builder.addField("classifier", obj.classifier)
      builder.addField("tpe", obj.tpe)
      builder.addField("transitives", obj.transitives)
      builder.addField("artifacts", obj.artifacts)
      builder.endObject()
    }
  }
}

trait ConfigurationLockFormats {
  self: sjsonnew.BasicJsonProtocol & ArtifactLockFormats & DependencyLockFormats =>
  given ConfigurationLockFormat: JsonFormat[ConfigurationLock] = new JsonFormat[ConfigurationLock] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): ConfigurationLock =
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val name = unbuilder.readField[String]("name")
          val dependencies = unbuilder.readField[Vector[DependencyLock]]("dependencies")
          unbuilder.endObject()
          ConfigurationLock(name, dependencies)
        case None =>
          deserializationError("Expected JsObject but found None")
      }

    override def write[J](obj: ConfigurationLock, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("name", obj.name)
      builder.addField("dependencies", obj.dependencies)
      builder.endObject()
    }
  }
}

trait RequestedDependencyFormats { self: sjsonnew.BasicJsonProtocol =>
  given RequestedDependencyFormat: JsonFormat[RequestedDependency] =
    new JsonFormat[RequestedDependency] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): RequestedDependency =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val configuration = unbuilder.readField[String]("configuration")
            val organization = unbuilder.readField[String]("organization")
            val name = unbuilder.readField[String]("name")
            val version = unbuilder.readField[String]("version")
            val variantSelector = unbuilder.readField[String]("variantSelector")
            unbuilder.endObject()
            RequestedDependency(
              configuration = configuration,
              organization = organization,
              name = name,
              version = version,
              variantSelector = variantSelector
            )
          case None =>
            deserializationError("Expected JsObject but found None")
        }

      override def write[J](obj: RequestedDependency, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("configuration", obj.configuration)
        builder.addField("organization", obj.organization)
        builder.addField("name", obj.name)
        builder.addField("version", obj.version)
        builder.addField("variantSelector", obj.variantSelector)
        builder.endObject()
      }
    }
}

trait RequestedForceVersionFormats { self: sjsonnew.BasicJsonProtocol =>
  given RequestedForceVersionFormat: JsonFormat[RequestedForceVersion] =
    new JsonFormat[RequestedForceVersion] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): RequestedForceVersion =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val module = unbuilder.readField[String]("module")
            val version = unbuilder.readField[String]("version")
            unbuilder.endObject()
            RequestedForceVersion(
              module = module,
              version = version
            )
          case None =>
            deserializationError("Expected JsObject but found None")
        }

      override def write[J](obj: RequestedForceVersion, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("module", obj.module)
        builder.addField("version", obj.version)
        builder.endObject()
      }
    }
}

trait RequestedExclusionFormats { self: sjsonnew.BasicJsonProtocol =>
  given RequestedExclusionFormat: JsonFormat[RequestedExclusion] =
    new JsonFormat[RequestedExclusion] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): RequestedExclusion =
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val organization = unbuilder.readField[String]("organization")
            val name = unbuilder.readField[String]("name")
            unbuilder.endObject()
            RequestedExclusion(
              organization = organization,
              name = name
            )
          case None =>
            deserializationError("Expected JsObject but found None")
        }

      override def write[J](obj: RequestedExclusion, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("organization", obj.organization)
        builder.addField("name", obj.name)
        builder.endObject()
      }
    }
}

trait RequestedInputsFormats {
  self: sjsonnew.BasicJsonProtocol & RequestedDependencyFormats & RequestedForceVersionFormats &
    RequestedExclusionFormats =>
  given RequestedInputsFormat: JsonFormat[RequestedInputs] = new JsonFormat[RequestedInputs] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): RequestedInputs =
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val dependencies = unbuilder.readField[Vector[RequestedDependency]]("dependencies")
          val repositories = unbuilder.readField[Vector[String]]("repositories")
          val scalaVersion = unbuilder.readField[Option[String]]("scalaVersion")
          val maxIterations = unbuilder.readField[Int]("maxIterations")
          val forceVersions = unbuilder.readField[Vector[RequestedForceVersion]]("forceVersions")
          val exclusions = unbuilder.readField[Vector[RequestedExclusion]]("exclusions")
          val strict = unbuilder.readField[Option[String]]("strict")
          unbuilder.endObject()
          RequestedInputs(
            dependencies = dependencies,
            repositories = repositories,
            scalaVersion = scalaVersion,
            maxIterations = maxIterations,
            forceVersions = forceVersions,
            exclusions = exclusions,
            strict = strict,
          )
        case None =>
          deserializationError("Expected JsObject but found None")
      }

    override def write[J](obj: RequestedInputs, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("dependencies", obj.dependencies)
      builder.addField("repositories", obj.repositories)
      builder.addField("scalaVersion", obj.scalaVersion)
      builder.addField("maxIterations", obj.maxIterations)
      builder.addField("forceVersions", obj.forceVersions)
      builder.addField("exclusions", obj.exclusions)
      builder.addField("strict", obj.strict)
      builder.endObject()
    }
  }
}

trait LockFileMetadataFormats { self: sjsonnew.BasicJsonProtocol =>
  given LockFileMetadataFormat: JsonFormat[LockFileMetadata] = new JsonFormat[LockFileMetadata] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): LockFileMetadata =
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val sbtVersion = unbuilder.readField[String]("sbtVersion")
          val scalaVersion = unbuilder.readField[Option[String]]("scalaVersion")
          unbuilder.endObject()
          LockFileMetadata(sbtVersion, scalaVersion)
        case None =>
          deserializationError("Expected JsObject but found None")
      }

    override def write[J](obj: LockFileMetadata, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("sbtVersion", obj.sbtVersion)
      builder.addField("scalaVersion", obj.scalaVersion)
      builder.endObject()
    }
  }
}

trait LockFileDataFormats {
  self: sjsonnew.BasicJsonProtocol & ArtifactLockFormats & DependencyLockFormats &
    ConfigurationLockFormats & LockFileMetadataFormats & RequestedInputsFormats =>
  given LockFileDataFormat: JsonFormat[LockFileData] = new JsonFormat[LockFileData] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): LockFileData =
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val version = unbuilder.readField[String]("version")
          val requested = unbuilder.readField[RequestedInputs]("requested")
          val configurations = unbuilder.readField[Vector[ConfigurationLock]]("configurations")
          val metadata = unbuilder.readField[LockFileMetadata]("metadata")
          unbuilder.endObject()
          LockFileData(version, requested, configurations, metadata)
        case None =>
          deserializationError("Expected JsObject but found None")
      }

    override def write[J](obj: LockFileData, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("version", obj.version)
      builder.addField("requested", obj.requested)
      builder.addField("configurations", obj.configurations)
      builder.addField("metadata", obj.metadata)
      builder.endObject()
    }
  }
}

object LockFileFormats
    extends sjsonnew.BasicJsonProtocol
    with ArtifactLockFormats
    with DependencyLockFormats
    with ConfigurationLockFormats
    with LockFileMetadataFormats
    with RequestedDependencyFormats
    with RequestedForceVersionFormats
    with RequestedExclusionFormats
    with RequestedInputsFormats
    with LockFileDataFormats
