package lmcoursier.internal

import sjsonnew._
import java.time.Instant

trait LockFileFormats { self: sjsonnew.BasicJsonProtocol =>
  
  implicit lazy val dependencyLockFormat: JsonFormat[DependencyLock] = new JsonFormat[DependencyLock] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): DependencyLock = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val organization = unbuilder.readField[String]("organization")
          val name = unbuilder.readField[String]("name")
          val version = unbuilder.readField[String]("version")
          val configuration = unbuilder.readField[String]("configuration")
          val classifier = unbuilder.readField[Option[String]]("classifier")
          val tpe = unbuilder.readField[String]("type")
          val transitives = unbuilder.readField[Seq[String]]("transitives")
          unbuilder.endObject()
          DependencyLock(organization, name, version, configuration, classifier, tpe, transitives)
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: DependencyLock, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("organization", obj.organization)
      builder.addField("name", obj.name)
      builder.addField("version", obj.version)
      builder.addField("configuration", obj.configuration)
      builder.addField("classifier", obj.classifier)
      builder.addField("type", obj.`type`)
      builder.addField("transitives", obj.transitives)
      builder.endObject()
    }
  }

  implicit lazy val configurationLockFormat: JsonFormat[ConfigurationLock] = new JsonFormat[ConfigurationLock] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): ConfigurationLock = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val name = unbuilder.readField[String]("name")
          val dependencies = unbuilder.readField[Seq[DependencyLock]]("dependencies")
          unbuilder.endObject()
          ConfigurationLock(name, dependencies)
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: ConfigurationLock, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("name", obj.name)
      builder.addField("dependencies", obj.dependencies)
      builder.endObject()
    }
  }

  implicit lazy val instantFormat: JsonFormat[Instant] = new JsonFormat[Instant] {
    def write[J](obj: Instant, builder: Builder[J]): Unit = {
      builder.writeString(obj.toString)
    }
    def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): Instant = {
      jsOpt match {
        case Some(js) =>
          val str = unbuilder.readString(js)
          Instant.parse(str)
        case None =>
          deserializationError("Expected JString for Instant")
      }
    }
  }

  implicit lazy val lockFileMetadataFormat: JsonFormat[LockFileMetadata] = new JsonFormat[LockFileMetadata] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): LockFileMetadata = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val sbtVersion = unbuilder.readField[String]("sbtVersion")
          val scalaVersion = unbuilder.readField[Option[String]]("scalaVersion")
          val timestamp = unbuilder.readField[Instant]("timestamp")
          unbuilder.endObject()
          LockFileMetadata(sbtVersion, scalaVersion, timestamp)
        case None =>
          deserializationError("Expected JsObject but found None")
      }
    }
    override def write[J](obj: LockFileMetadata, builder: Builder[J]): Unit = {
      builder.beginObject()
      builder.addField("sbtVersion", obj.sbtVersion)
      builder.addField("scalaVersion", obj.scalaVersion)
      builder.addField("timestamp", obj.timestamp)
      builder.endObject()
    }
  }

  implicit lazy val lockFileDataFormat: JsonFormat[LockFileData] = new JsonFormat[LockFileData] {
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): LockFileData = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val version = unbuilder.readField[String]("version")
          val buildClock = unbuilder.readField[String]("buildClock")
          val configurations = unbuilder.readField[Seq[ConfigurationLock]]("configurations")
          val metadata = unbuilder.readField[LockFileMetadata]("metadata")
          unbuilder.endObject()
          LockFileData(version, buildClock, configurations, metadata)
        case None =>
          deserializationError("Expected JsObject but found None")
      }
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

object LockFileFormats extends LockFileFormats with sjsonnew.BasicJsonProtocol
