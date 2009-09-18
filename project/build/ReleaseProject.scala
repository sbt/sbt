/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
import sbt._

import java.io.File

trait ReleaseProject extends ExecProject
{ self: SbtProject =>
	def info: ProjectInfo
	lazy val releaseChecks = javaVersionCheck && projectVersionCheck && fullyCheckedOut
	lazy val fullyCheckedOut =
		task
		{
			if(svnArtifactsPath.exists) None
			else Some("You need a fully checked out sbt repository with commit rights to do a release.")
		}
	lazy val javaVersionCheck =
		task
		{
			val javaVersion = System.getProperty("java.version")
			if(!javaVersion.startsWith("1.5."))
				Some("Java version must be 1.5.x (was " + javaVersion + ")")
			else
				None
		}
	lazy val projectVersionCheck =
		task
		{
			def value(a: Option[Int]) = a.getOrElse(0)
			def lessThan(a: Option[Int], b: Option[Int]) = value(a) < value(b)
			version match
			{
				case BasicVersion(major, minor, micro, None) =>
					Version.fromString(sbtVersion.value) match
					{
						case Right(BasicVersion(builderMajor, builderMinor, builderMicro, None))
							if (builderMajor < major || ( builderMajor == major &&
								lessThan(builderMinor, minor) || (builderMinor == minor &&
								lessThan(builderMicro, micro ) ))) =>
								None
						case _ => Some("Invalid builder sbt version.  Must be a release version older than the project version.  (was: " + sbtVersion.value + ")")
					}
				case _ => Some("Invalid project version.  Should be of the form #.#.# (was: " + version + ")")
			}
		}

	def svnURL = "https://simple-build-tool.googlecode.com/svn/"
	def latestURL = svnURL + "artifacts/latest"

	def svnArtifactsPath = Path.fromFile(info.projectPath.asFile.getParentFile) / "artifacts"
	def svnArtifactPath = svnArtifactsPath / version.toString
	def ivyLocalPath = Path.userHome / ".ivy2" / "local" / "sbt" / "simple-build-tool" / version.toString
	def manualTasks =
		("Upload launcher jar: " + boot.outputJar.absolutePath) ::
		"Update, build, check and commit Hello Lift example" ::
		Nil

	lazy val copyDocs = main.copyTask ( (main.mainDocPath ##) ** "*", svnArtifactPath / "api") dependsOn(main.doc, releaseChecks)
	lazy val copyIvysJars = main.copyTask( (ivyLocalPath ##) ** "*", svnArtifactPath) dependsOn(main.crossPublishLocal, releaseChecks)

	lazy val release = manualTaskMessage dependsOn(commitDocs, releaseArtifacts, releaseChecks)
	lazy val releaseArtifacts = nextVersion dependsOn(tag, latestLink, boot.proguard, releaseChecks)
	lazy val manualTaskMessage = task { println("The following tasks must be done manually:\n\t" + manualTasks.mkString("\n\t")); None }

	import sbt.ProcessXML._
	lazy val addArtifacts = execTask {<o> svn add {svnArtifactPath} </o>} dependsOn ( copyIvysJars, copyDocs, releaseChecks )
	lazy val commitArtifacts = execTask {<o> svn commit -m "Jars, Ivys, and API Docs for {version.toString}" {svnArtifactPath} </o>} dependsOn(addArtifacts, releaseChecks)
	lazy val tag = execTask {<o> svn copy -m "Tagging {version.toString}" {svnURL}/trunk/ {svnURL}/tags/{version.toString} </o>} dependsOn(releaseChecks)
	lazy val latestLink = (deleteLatestLink && makeLatestLink) dependsOn(commitArtifacts, releaseChecks)
	lazy val makeLatestLink = execTask {<o> svn copy -m "Creating new latest link" {svnURL}/artifacts/{version.toString}/ {latestURL} </o>} dependsOn(releaseChecks)
	lazy val deleteLatestLink = execTask {<o> svn del -m "Deleting old latest link" {latestURL} </o>} dependsOn(releaseChecks)
	lazy val commitProperties = execTask {<o> svn commit -m "Bumping versions" project/build.properties  </o>} dependsOn(releaseChecks)
	lazy val commitDocs = execTask {<o> svn commit -m "Updated documentation for {version.toString}" ../wiki/ </o>} dependsOn(releaseChecks)

	lazy val nextVersion =  (incrementVersions && commitProperties) dependsOn(releaseChecks)
	lazy val incrementVersions = task { incrementVersionNumbers(); None }
	def incrementVersionNumbers(): Unit =
		for( v <- projectVersion)
		{
			sbtVersion() = v.toString
			val incremented = v.asInstanceOf[BasicVersion].incrementMicro // BasicVersion checked by releaseChecks
			import incremented._
			val newVersion = BasicVersion(major, minor, micro, Some("SNAPSHOT"))
			log.info("Changing version to " + newVersion)
			projectVersion() = newVersion
			saveEnvironment
		}
}

package sbt {
	object ProcessXML {
		implicit def elemToPB(command: scala.xml.Elem): ProcessBuilder =
			impl.CommandParser.parse(command.text.trim).fold(error, Function.tupled(Process.apply))
	}
}