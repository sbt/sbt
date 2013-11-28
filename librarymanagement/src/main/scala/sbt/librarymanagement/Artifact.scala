/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.librarymanagement

import java.io.File
import java.net.URL
import sbt.serialization._

final case class Artifact(name: String, `type`: String, extension: String, classifier: Option[String], configurations: Iterable[Configuration], url: Option[URL], extraAttributes: Map[String, String]) {
  def extra(attributes: (String, String)*) = copy(extraAttributes = extraAttributes ++ ModuleID.checkE(attributes))
}

import Configurations.{ config, Optional, Pom, Test }

object Artifact {
  def apply(name: String): Artifact = Artifact(name, DefaultType, DefaultExtension, None, Nil, None)
  def apply(name: String, extra: Map[String, String]): Artifact = Artifact(name, DefaultType, DefaultExtension, None, Nil, None, extra)
  def apply(name: String, classifier: String): Artifact = Artifact(name, DefaultType, DefaultExtension, Some(classifier), Nil, None)
  def apply(name: String, `type`: String, extension: String): Artifact = Artifact(name, `type`, extension, None, Nil, None)
  def apply(name: String, `type`: String, extension: String, classifier: String): Artifact = Artifact(name, `type`, extension, Some(classifier), Nil, None)
  def apply(name: String, url: URL): Artifact = Artifact(name, extract(url, DefaultType), extract(url, DefaultExtension), None, Nil, Some(url))
  def apply(name: String, `type`: String, extension: String, classifier: Option[String], configurations: Iterable[Configuration], url: Option[URL]): Artifact =
    Artifact(name, `type`, extension, classifier, configurations, url, Map.empty)

  val DefaultExtension = "jar"
  val DefaultType = "jar"

  def sources(name: String) = classified(name, SourceClassifier)
  def javadoc(name: String) = classified(name, DocClassifier)
  def pom(name: String) = Artifact(name, PomType, PomType, None, Pom :: Nil, None)

	// Possible ivy artifact types such that sbt will treat those artifacts at sources / docs
	val DefaultSourceTypes = Set("src", "source", "sources")
	val DefaultDocTypes = Set("doc", "docs", "javadoc", "javadocs")

	val DocClassifier = "javadoc"
	val SourceClassifier = "sources"

	val TestsClassifier = "tests"
	// Artifact types used when:
	// * artifacts are explicitly created for Maven dependency resolution (see updateClassifiers)
	// * declaring artifacts as part of publishing Ivy files.
	val DocType = "doc"
	val SourceType = "src"
	val PomType = "pom"

	assert (DefaultDocTypes contains DocType)
	assert (DefaultSourceTypes contains SourceType)

  def extract(url: URL, default: String): String = extract(url.toString, default)
  def extract(name: String, default: String): String =
    {
      val i = name.lastIndexOf('.')
      if (i >= 0)
        name.substring(i + 1)
      else
        default
    }
  def defaultArtifact(file: File) =
    {
      val name = file.getName
      val i = name.lastIndexOf('.')
      val base = if (i >= 0) name.substring(0, i) else name
      Artifact(base, extract(name, DefaultType), extract(name, DefaultExtension), None, Nil, Some(file.toURI.toURL))
    }
  def artifactName(scalaVersion: ScalaVersion, module: ModuleID, artifact: Artifact): String =
    {
      import artifact._
      val classifierStr = classifier match { case None => ""; case Some(c) => "-" + c }
      val cross = CrossVersion(module.crossVersion, scalaVersion.full, scalaVersion.binary)
      val base = CrossVersion.applyCross(artifact.name, cross)
      base + "-" + module.revision + classifierStr + "." + artifact.extension
    }

	 val classifierTypeMap = Map(SourceClassifier -> SourceType, DocClassifier -> DocType)
	 // TODO this function shouldn't exist. Configuration should not just be conjured up like that.
	 def classifierConf(classifier: String): Configuration =
	 	 if(classifier.startsWith(TestsClassifier))
	 		  Test
	 	 else
	 	  	Optional
	 def classifierType(classifier: String): String = classifierTypeMap.getOrElse(classifier.stripPrefix(TestsClassifier + "-"), DefaultType)
	 def classified(name: String, classifier: String): Artifact =
	 	 Artifact(name, classifierType(classifier), DefaultExtension, Some(classifier), classifierConf(classifier) :: Nil, None)

	 private val optStringPickler = implicitly[Pickler[Option[String]]]
  private val optStringUnpickler = implicitly[Unpickler[Option[String]]]
  private val vectorConfigurationPickler = implicitly[Pickler[Vector[Configuration]]]
  private val vectorConfigurationUnpickler = implicitly[Unpickler[Vector[Configuration]]]
  private val stringStringMapPickler = implicitly[Pickler[Map[String, String]]]
  private val stringStringMapUnpickler = implicitly[Unpickler[Map[String, String]]]

  implicit val pickler: Pickler[Artifact] = new Pickler[Artifact] {
    val tag = implicitly[FastTypeTag[Artifact]]
    val stringTag = implicitly[FastTypeTag[String]]
    val optionStringTag = implicitly[FastTypeTag[Option[String]]]
    val vectorConfigurationTag = implicitly[FastTypeTag[Vector[Configuration]]]
    val stringStringMapTag = implicitly[FastTypeTag[Map[String, String]]]
    def pickle(a: Artifact, builder: PBuilder): Unit = {
      builder.pushHints()
      builder.hintTag(tag)
      builder.beginEntry(a)
      builder.putField("name", { b =>
        b.hintTag(stringTag)
        stringPickler.pickle(a.name, b)
      })
      builder.putField("type", { b =>
        b.hintTag(stringTag)
        stringPickler.pickle(a.`type`, b)
      })
      builder.putField("extension", { b =>
        b.hintTag(stringTag)
        stringPickler.pickle(a.extension, b)
      })
      builder.putField("classifier", { b =>
        b.hintTag(optionStringTag)
        optStringPickler.pickle(a.classifier, b)
      })
      builder.putField("configurations", { b =>
        b.hintTag(vectorConfigurationTag)
        vectorConfigurationPickler.pickle(a.configurations.toVector, b)
      })
      builder.putField("url", { b =>
        b.hintTag(optionStringTag)
        optStringPickler.pickle(a.url map { _.toString }, b)
      })
      builder.putField("extraAttributes", { b =>
        b.hintTag(stringStringMapTag)
        stringStringMapPickler.pickle(a.extraAttributes, b)
      })
      builder.endEntry()
      builder.popHints()
      ()
    }
  }
  implicit val unpickler: Unpickler[Artifact] = new Unpickler[Artifact] {
    val tag = implicitly[FastTypeTag[Artifact]]
    def unpickle(tpe: String, reader: PReader): Any = {
      reader.pushHints()
      // reader.hintTag(tag)
      reader.beginEntry()
      val name = stringPickler.unpickleEntry(reader.readField("name")).asInstanceOf[String]
      val tp = stringPickler.unpickleEntry(reader.readField("type")).asInstanceOf[String]
      val extension = stringPickler.unpickleEntry(reader.readField("extension")).asInstanceOf[String]
      val classifier = optStringUnpickler.unpickleEntry(reader.readField("classifier")).asInstanceOf[Option[String]]
      val configurations = vectorConfigurationUnpickler.unpickleEntry(reader.readField("configurations")).asInstanceOf[Vector[Configuration]]
      val u = optStringUnpickler.unpickleEntry(reader.readField("url")).asInstanceOf[Option[String]] map { new URL(_) }
      val extraAttributes = stringStringMapUnpickler.unpickleEntry(reader.readField("extraAttributes")).asInstanceOf[Map[String, String]]
      val result = Artifact(name, tp, extension, classifier, configurations, u, extraAttributes)
      reader.endEntry()
      reader.popHints()
      result
    }
  }
}
