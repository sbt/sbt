/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
import sbt._

import java.io.File

class SbtProject(info: ProjectInfo) extends DefaultProject(info) //with test.SbtScripted
{
	/* Additional resources to include in the produced jar.*/
	def extraResources = descendents(info.projectPath / "licenses", "*") +++ "LICENSE" +++ "NOTICE"
	override def mainResources = super.mainResources +++ extraResources

	override def testOptions = ExcludeTests("sbt.ReflectiveSpecification" :: Nil) :: super.testOptions.toList
	override def normalizedName = "sbt"
	//override def scriptedDependencies = testCompile :: `package` :: Nil

	/* For Scala 2.8.0 nightlies, use a special resolver so that we can refer to a specific nightly and not just 2.8.0-SNAPSHOT*/
	def specificSnapshotRepo =
		Resolver.url("scala-nightly") artifacts("http://scala-tools.org/repo-snapshots/[organization]/[module]/2.8.0-SNAPSHOT/[artifact]-[revision].[ext]") mavenStyle()
	val nightlyScala = ModuleConfiguration("org.scala-lang", "*", "2.8.0-.*", specificSnapshotRepo)

	override def compileOptions = Nil

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
	private val Version2_7_2 = "2.7.2"
	private val Version2_7_3 = "2.7.3"
	private val Version2_7_4 = "2.7.4"
	private val Version2_7_5 = "2.7.5"
	private val Version2_7_6 = "2.7.6"
	private val Version2_8_0 = "2.8.0-20090929.004247-+"
	// the list of all supported versions
	private def allVersions = Version2_7_2 :: Version2_7_3 :: Version2_7_4 :: Version2_7_5 :: Version2_7_6 :: Version2_8_0 :: Nil

	override def crossScalaVersions = Set(Version2_7_2, Version2_7_5)//, Version2_8_0)

	val ivy = "org.apache.ivy" % "ivy" % "2.0.0" intransitive()
	val jsch = "com.jcraft" % "jsch" % "0.1.31" intransitive()
	val jetty = "org.mortbay.jetty" % "jetty" % "6.1.14" % "optional"

	val testInterface = "org.scala-tools.testing" % "test-interface" % "0.1"

	// xsbt components
	val xsbti = "org.scala-tools.sbt" % "launcher-interface" % projectVersion.value.toString % "provided"
	val compiler = "org.scala-tools.sbt" %% "compile" % projectVersion.value.toString

	override def libraryDependencies = super.libraryDependencies ++ getDependencies(scalaVersionString)

	def getDependencies(scalaVersion: String) =
		scalaVersion match
		{
			case Version2_7_2 => variableDependencies(false, scalaVersion, /*ScalaTest*/"0.9.3", /*Specs*/"1.4.0", false)
			case Version2_7_3 => variableDependencies(false, scalaVersion, /*ScalaTest*/"0.9.4", /*Specs*/"1.4.3", true)
			case Version2_7_4 => variableDependencies(false, scalaVersion, /*ScalaTest*/"0.9.5", /*Specs*/"1.4.3", true)
			case Version2_7_5 => variableDependencies(false, scalaVersion, /*ScalaTest*/"0.9.5", /*Specs*/"1.4.3", true)
			case Version2_7_6 => variableDependencies(false, scalaVersion, /*ScalaTest*/"0.9.5", /*Specs*/"1.4.3", true)
			case Version2_8_0 => variableDependencies(true,  scalaVersion, /*ScalaTest*/"0.9.5", /*Specs*/"1.4.3", true)
			case _ => error("Unsupported Scala version: " + scalaVersion)
		}

	/** Defines the dependencies for the given version of Scala, ScalaTest, and Specs.  If uniformTestOrg is true,
	* the 'org.scala-tools.testing' organization is used.  Otherwise, 'org.' is prefixed to the module name. */
	private def variableDependencies(is28: Boolean, scalaVersion: String, scalaTestVersion: String, specsVersion: String, uniformTestOrg: Boolean) =
	{
		( if(is28) Nil else testDependencies(scalaTestVersion, specsVersion, uniformTestOrg)) ++
			( if(is28) Seq("jline" % "jline" % "0.9.94" intransitive()) else Nil)
	}
	private def testDependencies(scalaTestVersion: String, specsVersion: String, uniformTestOrg: Boolean) =
	{
		testDependency("scalatest", scalaTestVersion, uniformTestOrg) ::
		testDependency("specs", specsVersion, uniformTestOrg) ::
		testDependency("scalacheck", "1.5", false) ::
		Nil
	}

	/** Creates a dependency element for a test.  See 'testOrg' for a description of uniformTestOrg.*/

	private def testDependency(name: String, version: String, uniformTestOrg: Boolean) =
		testOrg(name, uniformTestOrg) % name % version % "optional" intransitive()

	/** Returns the organization for the given test library.  If uniform is true,
	* the 'org.scala-tools.testing' organization is used.  Otherwise, 'org.' is prefixed to the module name.*/
	private def testOrg(name: String, uniform: Boolean) = if(uniform) "org.scala-tools.testing" else "org." + name
}