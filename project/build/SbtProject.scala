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

	override def testOptions = ExcludeTests("sbt.ReflectiveSpecification" :: Nil) :: super.testOptions.toList
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
	override def scriptedBuildVersions = "2.7.7 2.7.5 2.7.2 2.8.0 2.8.1.RC4"

	override def useDefaultConfigurations = false
	val default = Configurations.Default
	val optional = Configurations.Optional
	val provided = Configurations.Provided
	val testConf = Configurations.Test
	val docConf = Configurations.Javadoc
	val srcConf = Configurations.Sources

	//testing
	val scalacheck = "org.scala-tools.testing" %% "scalacheck" % "1.6" % "test"

	val ivy = "org.apache.ivy" % "ivy" % "2.2.0" intransitive()
	val jsch = "com.jcraft" % "jsch" % "0.1.31" intransitive()

	override def libraryDependencies = super.libraryDependencies ++
		jetty6Dependencies ++
		jetty7Dependencies

	def jetty6Dependencies = Set(
		"org.mortbay.jetty" % "jetty" % "6.1.14" % "optional",
		"org.mortbay.jetty" % "jetty-plus" % "6.1.14" % "optional"
	)
	def jetty7Dependencies = Set(
		"org.eclipse.jetty" % "jetty-server" % "7.0.1.v20091125" % "optional",
		"org.eclipse.jetty" % "jetty-webapp" % "7.0.1.v20091125" % "optional",
		"org.eclipse.jetty" % "jetty-plus" % "7.0.1.v20091125" % "optional"
	)
	def jetty72Dependencies = Set(
		"org.eclipse.jetty" % "jetty-server" % "7.2.0.v20101020" % "optional",
		"org.eclipse.jetty" % "jetty-webapp" % "7.2.0.v20101020" % "optional",
		"org.eclipse.jetty" % "jetty-plus" % "7.2.0.v20101020" % "optional"
	)

	val testInterface = "org.scala-tools.testing" % "test-interface" % "0.5"

	def concatPaths[T](s: Seq[T])(f: PartialFunction[T, PathFinder]): PathFinder =
	{
		def finder: T => PathFinder = (f orElse { case _ => Path.emptyPathFinder })
		(Path.emptyPathFinder /: s) { _ +++ finder(_) }
	}
	def deepSources = concatPaths(topologicalSort){ case p: ScalaPaths => p.mainSources } --- jetty72Compat.mainSources
	lazy val sbtGenDoc = scaladocTask("sbt", deepSources, docPath, docClasspath, documentOptions) dependsOn(compile)
	lazy val sbtDoc = packageTask(mainDocPath ##, packageDocsJar, Recursive) dependsOn(sbtGenDoc)
	lazy val sbtSrc = packageTask(deepSources, packageSrcJar, packageOptions) dependsOn(compile)
	
	override def packagePaths = super.packagePaths +++ jetty6Compat.packagePaths
	override def packageToPublishActions = super.packageToPublishActions //++ Seq(sbtSrc, sbtDoc, sxr)
	
	override def packageDocsJar = defaultJarPath("-javadoc.jar")
	override def packageSrcJar= defaultJarPath("-sources.jar")
	/*val sourceArtifact = Artifact(artifactID, "src", "jar", "sources")
	val docsArtifact = Artifact(artifactID, "doc", "jar", "javadoc")*/

	/* For generating JettyRun for Jetty 6 and 7.  The only difference is the imports, but the file has to be compiled against each set of imports. */
	override def compileAction = super.compileAction dependsOn (generateJettyRun6, generateJettyRun7)
	def jettySrcDir = mainScalaSourcePath / "sbt" / "jetty"
	def jettyImports(version: String) = jettySrcDir / ("jetty" + version + ".imports")
	def jettyTemplate = jettySrcDir / "LazyJettyRun.scala.templ"
	
	lazy val generateJettyRun6 = generateJettyRun(jettyTemplate, jettySrcDir / "LazyJettyRun6.scala", "6", jettyImports("6"))
	lazy val generateJettyRun7 = generateJettyRun(jettyTemplate, jettySrcDir / "LazyJettyRun7.scala", "7", jettyImports("7"))
	def generateJettyRun(in: Path, out: Path, version: String, importsPath: Path) =
		task
		{
			(for(template <- FileUtilities.readString(in asFile, log).right; imports <- FileUtilities.readString(importsPath asFile, log).right) yield
				FileUtilities.write(out asFile, processJettyTemplate(template, version, imports), log).toLeft(()) ).left.toOption
		}
	def processJettyTemplate(template: String, version: String, imports: String): String =
		template.replaceAll("""\Q${jetty.version}\E""", version).replaceAll("""\Q${jetty.imports}\E""", imports)

	final class JettyCompat(info: ProjectInfo, version: String, override val libraryDependencies: Set[ModuleID]) extends DefaultProject(info) with NoPublish
	{
		def outName = "SbtWebAppLoader" + version + ".scala"
		def imports = "jetty" + version + ".imports"
		override def managedDependencyPath = super.managedDependencyPath / ("jetty" + version)

		lazy val generateLoaderCompat = generateJettyRun("SbtWebAppLoader.scala.templ", outName, version, jettySrcDir / imports)
		override def mainSources = outName : Path
		override def compileAction = super.compileAction dependsOn generateLoaderCompat
	}

	lazy val jetty6Compat = project("jetty-compat", "Jetty 6.x Compat", i => new JettyCompat(i, "6", jetty6Dependencies))
	lazy val jetty7Compat = project("jetty-compat", "Jetty 7.0-1 Compat", i => new JettyCompat(i, "7", jetty7Dependencies))
	lazy val jetty72Compat = project("jetty-compat", "Jetty 7.2 Compat", i => new JettyCompat(i, "72", jetty72Dependencies))
	override def deliverProjectDependencies = Set() ++ super.deliverProjectDependencies - jetty6Compat.projectID - jetty7Compat.projectID - jetty72Compat.projectID
}
