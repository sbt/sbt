import sbt._

import java.io.File
import scala.xml.NodeSeq

/** Support for compiling sbt across multiple versions of Scala.  The scala compiler is run in a
* separate JVM and no partial compilation is done.*/
abstract class CrossCompileProject extends BasicScalaProject with MavenStyleScalaPaths
{
	/** Used for 2.8.0-SNAPSHOT*/
	val scalaToolsSnapshots = "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots"

	/* The base configuration names for the versions of Scala*/
	private val version2_7_2 = "2.7.2"
	private val version2_7_3 = "2.7.3"
	private val version2_7_4 = "2.7.4"
	private val version2_7_5 = "2.7.5"
	private val version2_8_0 = "2.8.0.r18093-b20090623200909"
	private val base = "base"

	/* The configurations for the versions of Scala.*/
	private val conf_2_7_2 = config(version2_7_2)
	private val conf_2_7_3 = config(version2_7_3)
	private val conf_2_7_4 = config(version2_7_4)
	private val conf_2_7_5 = config(version2_7_5)
	private val conf_2_8_0 = config(version2_8_0)
	private val conf_base = config(base)
	// the list of all configurations cross-compile supports
	private val allConfigurations = conf_2_7_2 :: conf_2_7_3 :: conf_2_7_4 :: conf_2_7_5 :: conf_2_8_0 :: Nil
	// the list of configurations to actually build against
	private val buildConfigurations = conf_2_7_2 :: conf_2_7_3 :: conf_2_7_4 :: conf_2_7_5 :: Nil//allConfigurations not currently used because of issues with 2.8.0
	// the configuration to use for normal development (when cross-building is not done)
	private def developmentVersion = buildConfigurations.first
	
	/* Methods to derive the configuration name from the base name 'v'.*/
	private def optional(v: Configuration) = config("optional-" + v.toString)
	private def scalac(v: Configuration) = config("scalac-" + v.toString)
	private def sbt(v: Configuration) = config("sbt_" + v.toString)
	private def depConf(v: Configuration) = v.toString + "->default"
	
	// =========== Cross-compilation across scala versions ===========
	
	// The dependencies that should go in each configuration are:
	//   base                             Required dependencies that are the same across all scala versions.
	//   <version>                  Required dependencies to use with Scala <version>
	//   optional-base              Optional dependencies that are the same for all scala versions
	//   optional-<version>   Optional dependencies to use with Scala <version>
	//   compile                        Used for normal development, it should extend a specific <version> and optional-<version>
	//   scalac-<version>       The scala compiler for Scala <version>
	// There should be a jar publication for each version of scala.  The artifact should be named sbt_<version>.
	override def ivyXML =
		(<configurations>
			<conf name={conf_base.toString}/>
			<conf name={optional(conf_base).toString}/>
			{ variableConfigurations }
			<!-- The configuration used for normal development (actions other than cross-*) -->
			<conf name="default" extends={developmentVersion + "," + optional(developmentVersion).toString} visibility="private"/>
		</configurations>
		<publications>
			{ publications }
		</publications>
		<dependencies>
			<!-- Dependencies that are the same across all Scala versions -->
			<dependency org="org.apache.ivy" name="ivy" rev="2.0.0" transitive="false" conf={depConf(conf_base)}/>
			<dependency org="com.jcraft" name="jsch" rev="0.1.31" transitive="false" conf={depConf(conf_base)}/>
			<dependency org="org.mortbay.jetty" name="jetty" rev="6.1.14" transitive="true" conf={depConf(optional(conf_base))}/>

			<!-- the dependencies that are different dependeding on the version of Scala -->
			{ variableDependencies(conf_2_7_2, /*ScalaTest*/"0.9.3", /*Specs*/"1.4.0", false) }
			{ variableDependencies(conf_2_7_3, /*ScalaTest*/"0.9.4", /*Specs*/"1.4.3", true) }
			{ variableDependencies(conf_2_7_4, /*ScalaTest*/"0.9.5", /*Specs*/"1.4.3", true) }
			{ variableDependencies(conf_2_7_5, /*ScalaTest*/"0.9.5", /*Specs*/"1.4.3", true) }
			{ variableDependencies(conf_2_8_0, /*ScalaTest*/"0.9.5", /*Specs*/"1.4.3", true) }
		</dependencies>)

	/** Creates a publication (an 'artifact' element) for each Scala version */
	private def publications: NodeSeq =
	{
		for(conf <- buildConfigurations) yield
			<artifact name={sbt(conf).toString} conf={conf.toString}/>
	}
	/** Creates the main, optional, and scalac configurations for each Scala version*/
	private def variableConfigurations: NodeSeq =
	{
		buildConfigurations flatMap
		{ conf =>
			scalaComment(conf) ++
			(<conf name={conf.toString} extends={conf_base.toString}/>
			<conf name={optional(conf).toString} extends={optional(conf_base).toString}/>
			<conf name={scalac(conf).toString} visibility="private"/>)
		}
	}
	/** Defines the dependencies for the given version of Scala, ScalaTest, and Specs.  If uniformTestOrg is true,
	* the 'org.scala-tools.testing' organization is used.  Otherwise, 'org.' is prefixed to the module name. */
	private def variableDependencies(scalaVersion: Configuration, scalaTestVersion: String, specsVersion: String, uniformTestOrg: Boolean) =
	{
		if(buildConfigurations.contains(scalaVersion))
		{
			scalaComment(scalaVersion) ++
			{
				if(scalaVersion eq conf_2_8_0)
					Nil
				else
				{
					testDependency("scalatest", scalaTestVersion, uniformTestOrg, scalaVersion) ++
					testDependency("specs", specsVersion, uniformTestOrg, scalaVersion) ++
					testDependency("scalacheck", "1.5", false, scalaVersion)
				}
			} ++
			scalaDependency("scala-compiler", scalaVersion) ++ scalaDependency("scala-library", scalaVersion) ++
			{
				if(scalaVersion == conf_2_8_0)
					<dependency org="jline" name="jline" rev="0.9.94" transitive="false" conf={depConf(conf_2_8_0)}/>
				else
					NodeSeq.Empty
			}
		}
		else
			Nil
	}
	private def scalaDependency(name: String, scalaVersion: Configuration) =
		<dependency org="org.scala-lang" name={name} rev={scalaVersion.toString} conf={depConf(scalac(scalaVersion))}/>
	
	/** Creates a comment containing the version of Scala*/
	private def scalaComment(scalaVersion: Configuration) = scala.xml.Comment("Scala " + scalaVersion)
	/** Creates a dependency element for a test.  See 'testOrg' for a description of uniformTestOrg.*/

	private def testDependency(name: String, version: String, uniformTestOrg: Boolean, baseConf: Configuration) =
		<dependency org={testOrg(name, uniformTestOrg)} name={name} rev={version} transitive="false" conf={depConf(optional(baseConf))}/>
		
	/** Returns the organization for the given test library.  If uniform is true,
	* the 'org.scala-tools.testing' organization is used.  Otherwise, 'org.' is prefixed to the module name.*/
	private def testOrg(name: String, uniform: Boolean) =
		if(uniform) "org.scala-tools.testing"
		else "org." + name

	/** Disable filtering Scala jars from dependency management, because we need them and are putting them
	* in custom configurations and are using them in a separate process than sbt runs in.*/
	override def filterScalaJars = false
	
	/** The lib directory is now only for building using the 'build' script.*/
	override def unmanagedClasspath = path("ignore_lib_directory")
	/** When cross-compiling, replace mainCompilePath with the classes directory for the version being compiled.*/
	override def fullUnmanagedClasspath(config: Configuration) =
		if( (Configurations.Default :: Configurations.defaultMavenConfigurations) contains config)
			super.fullUnmanagedClasspath(config)
		else
			classesPath(config) +++ mainResourcesPath
	
	// include the optional-<version> dependencies as well as the ones common across all scala versions
	def optionalClasspath(version: Configuration) = fullClasspath(optional(version)) +++ super.optionalClasspath
	
	private val CompilerMainClass = "scala.tools.nsc.Main"
	// use a publish configuration that publishes the 'base' + all <version> configurations (base is required because
	//   the <version> configurations extend it)
	private val conf = new DefaultPublishConfiguration("local", "release")
	{
		override def configurations: Option[Iterable[Configuration]] = Some(config(base) :: buildConfigurations)
	}
	// the actions for cross-version packaging and publishing
	lazy val crossPackage = buildConfigurations.map(packageForScala)
	lazy val crossDeliverLocal = deliverTask(conf, updateOptions) dependsOn(crossPackage : _*)
	lazy val crossPublishLocal = publishTask(conf, updateOptions) dependsOn(crossDeliverLocal)
	// Creates a task that produces a packaged sbt compiled against Scala scalaVersion.
	//  The jar is named 'sbt_<scala-version>-<sbt-version>.jar'
	private def packageForScala(scalaVersion: Configuration) =
	{
		val classes = classesPath(scalaVersion) ** "*"
		val jarName = crossJarName(scalaVersion)
		val packageActionName = crossActionName("package", scalaVersion)
		val compileAction = compileForScala(scalaVersion) named(crossActionName("compile", scalaVersion))
		packageTask(classes +++ mainResources, outputPath, jarName, packageOptions) dependsOn(compileAction) named(packageActionName)
	}
	private def crossActionName(base: String, scalaVersion: Configuration) = base + " [ " + scalaVersion.toString + " ] "
	private def crossJarName(scalaVersion: Configuration) = sbt(scalaVersion) + "-" + version.toString +  ".jar"
	// This creates a task that compiles sbt against the given version of scala.  Classes are put in classes-<scalaVersion>.
	private def compileForScala(version: Configuration)=
		task
		{
			val classes = classesPath(version)
			val toClean = (outputPath / crossJarName(version)) +++ (classes ** "*")
			val setupResult =
				FileUtilities.clean(toClean.get, true, log) orElse
				FileUtilities.createDirectory(classes, log)
			for(err <- setupResult) log.error(err)
			// the classpath containing the scalac compiler
			val compilerClasspath = concatPaths(fullClasspath(scalac(version)))
			
			// The libraries to compile sbt against
			val classpath = fullClasspath(version) +++ optionalClasspath(version)
			val sources: List[String] = pathListStrings(mainSources)
			val compilerOptions = List("-cp", concatPaths(classpath), "-d", classes.toString)
			val compilerArguments: List[String] = compilerOptions ::: sources
			
			// the compiler classpath has to be appended to the boot classpath to work properly
			val allArguments = "-Xmx512M" :: ("-Xbootclasspath/a:" + compilerClasspath) :: CompilerMainClass :: compilerArguments
			log.debug("Running external compiler with command: java " + allArguments.mkString(" "))
			val exitValue = Process("java", allArguments) ! log
			if(exitValue == 0)
				None
			else
				Some("Nonzero exit value (" + exitValue + ") when calling scalac " + version + " with options: \n" + compilerOptions.mkString(" "))
		}
	private def concatPaths(p: PathFinder): String = Path.makeString(p.get)
	private def pathListStrings(p: PathFinder): List[String] = p.get.map(_.absolutePath).toList
	private def classesPath(scalaVersion: Configuration) = ("target"  / ("classes-" + scalaVersion.toString)) ##
}
