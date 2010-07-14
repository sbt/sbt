/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
import sbt._

import java.io.File
import java.net.URL

abstract class SbtProject(info: ProjectInfo) extends DefaultProject(info) with test.SbtScripted with posterous.Publish with Sxr
{
	/* Additional resources to include in the produced jar.*/
	def extraResources = descendents(info.projectPath / "licenses", "*") +++ "LICENSE" +++ "NOTICE"
	override def mainResources = super.mainResources +++ extraResources

	override def normalizedName = "sbt"

	override def managedStyle = ManagedStyle.Ivy
	//val publishTo = Resolver.file("technically", new File("/var/dbwww/repo/"))
	val technically = Resolver.url("technically.us", new URL("http://databinder.net/repo/"))(Resolver.ivyStylePatterns)

	override def compileOptions = CompileOption("-Xno-varargs-conversion") :: Nil

	/**  configuration of scripted testing **/
	// Set to false to show logging as it happens without buffering, true to buffer until it completes and only show if the task fails.
	//   The output of scripted tasks executed in parallel will be inteleaved if false.
	override def scriptedBufferLog = true
	// Configure which versions of Scala to test against for those tests that do cross building
	override def scriptedBuildVersions = "2.7.7 2.7.5 2.7.2 2.8.0.RC6 2.8.0.RC3"

	override def useDefaultConfigurations = false
	val default = Configurations.Default
	val optional = Configurations.Optional
	val provided = Configurations.Provided
	val testConf = Configurations.Test
	val docConf = Configurations.Javadoc
	val srcConf = Configurations.Sources

	//testing
	val scalacheck = "org.scala-tools.testing" %% "scalacheck" % "1.7" % "test"

	val ivy = "org.apache.ivy" % "ivy" % "2.1.0" intransitive()
	val jsch = "com.jcraft" % "jsch" % "0.1.31" intransitive()

	val testInterface = "org.scala-tools.testing" % "test-interface" % "0.5"

	def concatPaths[T](s: Seq[T])(f: PartialFunction[T, PathFinder]): PathFinder =
	{
		def finder: T => PathFinder = (f orElse { case _ => Path.emptyPathFinder })
		(Path.emptyPathFinder /: s) { _ +++ finder(_) }
	}
	def deepSources = concatPaths(topologicalSort){ case p: ScalaPaths => p.mainSources }
	lazy val sbtGenDoc = scaladocTask("sbt", deepSources, docPath, docClasspath, documentOptions) dependsOn(compile)
	lazy val sbtDoc = packageTask(mainDocPath ##, packageDocsJar, Recursive) dependsOn(sbtGenDoc)
	lazy val sbtSrc = packageTask(deepSources, packageSrcJar, packageOptions) dependsOn(compile)
	
	override def packageToPublishActions = super.packageToPublishActions //++ Seq(sbtSrc, sbtDoc, sxr)
	
	override def packageDocsJar = defaultJarPath("-javadoc.jar")
	override def packageSrcJar= defaultJarPath("-sources.jar")
	/*val sourceArtifact = Artifact(artifactID, "src", "jar", "sources")
	val docsArtifact = Artifact(artifactID, "doc", "jar", "javadoc")*/
}