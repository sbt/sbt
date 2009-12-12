/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
import sbt._

import java.io.File
import java.net.URL

class SbtProject(info: ProjectInfo) extends DefaultProject(info) with test.SbtScripted
{
	/* Additional resources to include in the produced jar.*/
	def extraResources = descendents(info.projectPath / "licenses", "*") +++ "LICENSE" +++ "NOTICE"
	override def mainResources = super.mainResources +++ extraResources

	override def testOptions = ExcludeTests("sbt.ReflectiveSpecification" :: Nil) :: super.testOptions.toList
	override def normalizedName = "sbt"

	override def managedStyle = ManagedStyle.Ivy
	//val publishTo = Resolver.file("technically", new File("/var/dbwww/repo/"))
	val technically = Resolver.url("technically.us", new URL("http://databinder.net/repo/"))(Resolver.ivyStylePatterns)

	override def compileOptions = Nil

	/**  configuration of scripted testing **/
	// Set to false to show logging as it happens without buffering, true to buffer until it completes and only show if the task fails.
	//   The output of scripted tasks executed in parallel will be inteleaved if true.
	override def scriptedBufferLog = true
	// Configure which versions of Scala to test against for those tests that do cross building
	override def scriptedCompatibility = sbt.test.CompatibilityLevel.Full

	def scalaVersionString = ScalaVersion.current.getOrElse(scalaVersion.value)
	override def mainSources =
	{
		if(scalaVersionString == Version2_8_0)
				Path.lazyPathFinder( super.mainSources.get.filter( !_.asFile.getName.endsWith("TestFrameworkImpl.scala") ))
		else
				super.mainSources
	}

	override def useDefaultConfigurations = false
	val default = Configurations.Default
	val optional = Configurations.Optional
	val provided = Configurations.Provided

	/* Versions of Scala to cross-build against. */
	private val Version2_7_7 = "2.7.7"
	private val Version2_8_0 = "2.8.0-SNAPSHOT"
	// the list of all supported versions
	private def allVersions = Version2_7_7 :: Version2_8_0 :: Nil

	override def crossScalaVersions = Set(Version2_7_7)

	//testing
	val scalacheck = "org.scala-tools.testing" %% "scalacheck" % "1.6"

	val ivy = "org.apache.ivy" % "ivy" % "2.0.0" intransitive()
	val jsch = "com.jcraft" % "jsch" % "0.1.31" intransitive()
	val jetty = "org.mortbay.jetty" % "jetty" % "6.1.14" % "optional"

	val testInterface = "org.scala-tools.testing" % "test-interface" % "0.2"

	// xsbt components
	val xsbti = "org.scala-tools.sbt" % "launcher-interface" % projectVersion.value.toString % "provided"
	val compiler = "org.scala-tools.sbt" %% "compile" % projectVersion.value.toString

	override def libraryDependencies = super.libraryDependencies ++ getDependencies(scalaVersionString)

	def getDependencies(scalaVersion: String) =
		if(scalaVersion == Version2_8_0) Seq("jline" % "jline" % "0.9.94" intransitive()) else Nil
}