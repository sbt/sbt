/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import java.net.URL

final case class Artifact(name: String, `type`: String, extension: String, classifier: Option[String], configurations: Iterable[Configuration], url: Option[URL], extraAttributes: Map[String,String])
{
	def extra(attributes: (String,String)*) = copy(extraAttributes = extraAttributes ++ ModuleID.checkE(attributes))
}

		import Configurations.{Compile, Pom, Test}

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

	// Possible ivy artifact types such that sbt will treat those artifacts at sources / docs
	val DefaultSourceTypes = Set("src", "source", "sources")
	val DefaultDocTypes = Set("doc", "docs", "javadoc", "javadocs")

	val DocClassifier = "javadoc"
	val SourceClassifier = "sources"

	val TestsClassifier = "tests"
	// Artifact types used when:
	// * artifacts are explicitly created for Maven dependency resolution (see updateClassifiers)
	// * declaring artifacts as part of creating Ivy files.
	val DocType = "doc"
	val SourceType = "src"
	val PomType = "pom"

	assert (DefaultDocTypes contains DocType)
	assert (DefaultSourceTypes contains SourceType)

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

	val classifierTypeMap = Map(SourceClassifier -> SourceType, DocClassifier -> DocType)

	@deprecated("Configuration should not be decided from the classifier.", "0.14.0")
	def classifierConf(classifier: String): Configuration =
		if(classifier.startsWith(TestsClassifier))
			Test
		else
			Compile

	def classifierType(classifier: String): String = classifierTypeMap.getOrElse(classifier.stripPrefix(TestsClassifier + "-"), DefaultType)

	/** Create a classified explicit artifact, to be used when trying to resolve sources|javadocs from Maven. This is
	  * necessary because those artifacts are not published in the Ivy generated from the Pom of the module in question.
	  * The artifact is created under the default configuration. */
	def classified(name: String, classifier: String): Artifact =
		Artifact(name, classifierType(classifier), DefaultExtension, Some(classifier), Nil, None)
}
