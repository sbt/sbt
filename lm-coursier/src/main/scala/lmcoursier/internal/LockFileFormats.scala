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
    ConfigurationLockFormats & LockFileMetadataFormats =>
  given LockFileDataFormat: JsonFormat[LockFileData] = new JsonFormat[LockFileData] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): LockFileData =
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val version = unbuilder.readField[String]("version")
          val buildClock = unbuilder.readField[String]("buildClock")
          val configurations = unbuilder.readField[Vector[ConfigurationLock]]("configurations")
          val metadata = unbuilder.readField[LockFileMetadata]("metadata")
          unbuilder.endObject()
          LockFileData(version, buildClock, configurations, metadata)
        case None =>
          deserializationError("Expected JsObject but found None")
      }

    override def write[J](obj: LockFileData, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("version", obj.version)
      builder.addField("buildClock", obj.buildClock)
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
    with LockFileDataFormats
