/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import java.net.URL

final case class Artifact(name: String, `type`: String, extension: String, classifier: Option[String], configurations: Iterable[Configuration], url: Option[URL], extraAttributes: Map[String,String])
{
	def extra(attributes: (String,String)*) = Artifact(name, `type`, extension, classifier, configurations, url, extraAttributes ++ ModuleID.checkE(attributes))
}

		import Configurations.{config, Docs, Optional, Pom, Sources}

object Artifact
{
	def apply(name: String): Artifact = Artifact(name, DefaultType, DefaultExtension, None, Nil, None)
	def apply(name: String, extra: Map[String,String]): Artifact = Artifact(name, DefaultType, DefaultExtension, None, Nil, None, extra)
	def apply(name: String, classifier: String): Artifact = Artifact(name, DefaultType, DefaultExtension, Some(classifier), Nil, None)
	def apply(name: String, `type`: String, extension: String): Artifact = Artifact(name, `type`, extension, None, Nil, None)
	def apply(name: String, `type`: String, extension: String, classifier: String): Artifact = Artifact(name, `type`, extension, Some(classifier), Nil, None)
	def apply(name: String, url: URL): Artifact =Artifact(name, extract(url, DefaultType), extract(url, DefaultExtension), None, Nil, Some(url))
	def apply(name: String, `type`: String, extension: String, classifier: Option[String], configurations: Iterable[Configuration], url: Option[URL]): Artifact =
		Artifact(name, `type`, extension, classifier, configurations, url, Map.empty)

	val DefaultExtension = "jar"
	val DefaultType = "jar"

	def sources(name: String) = classified(name, SourceClassifier)
	def javadoc(name: String) = classified(name, DocClassifier)
	def pom(name: String) =  Artifact(name, PomType, PomType, None, Pom :: Nil, None)

	val DocClassifier = "javadoc"
	val SourceClassifier = "sources"
	val DocType = "doc"
	val SourceType = "src"
	val PomType = "pom"

	def extract(url: URL, default: String): String = extract(url.toString, default)
	def extract(name: String, default: String): String =
	{
		val i = name.lastIndexOf('.')
		if(i >= 0)
			name.substring(i+1)
		else
			default
	}
	def defaultArtifact(file: File) =
	{
		val name = file.getName
		val i = name.lastIndexOf('.')
		val base = if(i >= 0) name.substring(0, i) else name
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

	val classifierConfMap = Map(SourceClassifier -> Sources, DocClassifier -> Docs)
	val classifierTypeMap = Map(SourceClassifier -> SourceType, DocClassifier -> DocType)
	def classifierConf(classifier: String): Configuration = classifierConfMap.getOrElse(classifier, Optional)
	def classifierType(classifier: String): String = classifierTypeMap.getOrElse(classifier.stripPrefix("test-"), DefaultType)
	def classified(name: String, classifier: String): Artifact =
		Artifact(name, classifierType(classifier), DefaultExtension, Some(classifier), classifierConf(classifier) :: Nil, None)
}
