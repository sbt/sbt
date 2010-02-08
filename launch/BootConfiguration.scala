/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
 package xsbt.boot

// <boot.directory>
//     scala-<scala.version>/    [baseDirectoryName]
//          lib/    [ScalaDirectoryName]
//          <app.name>-<app.version>/  [appDirectoryName]
//
// see also ProjectProperties for the set of constants that apply to the build.properties file in a project
private object BootConfiguration
{
	// these are the Scala module identifiers to resolve/retrieve
	val ScalaOrg = "org.scala-lang"
	val CompilerModuleName = "scala-compiler"
	val LibraryModuleName = "scala-library"

	val SbtOrg = "org.scala-tools.sbt"

	/** The Ivy conflict manager to use for updating.*/
	val ConflictManagerName = "strict"
	/** The name of the local Ivy repository, which is used when compiling sbt from source.*/
	val LocalIvyName = "local"
	/** The pattern used for the local Ivy repository, which is used when compiling sbt from source.*/
	val LocalPattern = "[organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]"
	/** The artifact pattern used for the local Ivy repository.*/
	def LocalArtifactPattern = LocalPattern
	/** The Ivy pattern used for the local Ivy repository.*/
	def LocalIvyPattern = LocalPattern

	/** The class name prefix used to hide the Scala classes used by this loader from the application */
	val ScalaPackage = "scala."
	/** The class name prefix used to hide the Ivy classes used by this loader from the application*/
	val IvyPackage = "org.apache.ivy."
	/** The class name prefix used to hide the launcher classes from the application.
	* Note that access to xsbti classes are allowed.*/
	val SbtBootPackage = "xsbt.boot."
	/** The prefix for JLine resources.*/
	val JLinePackagePath = "jline/"
	/** The loader will check that these classes can be loaded and will assume that their presence indicates
	* the Scala compiler and library have been downloaded.*/
	val TestLoadScalaClasses = "scala.Option" :: "scala.tools.nsc.Global" :: Nil

	val ScalaHomeProperty = "scala.home"
	val UpdateLogName = "update.log"

	val DefaultIvyConfiguration = "default"

	/** The name of the directory within the boot directory to retrieve scala to. */
	val ScalaDirectoryName = "lib"
	/** The Ivy pattern to use for retrieving the scala compiler and library.  It is relative to the directory
	* containing all jars for the requested version of scala. */
	val scalaRetrievePattern = ScalaDirectoryName + "/[artifact](-[classifier]).[ext]"

	/** The Ivy pattern to use for retrieving the application and its dependencies.  It is relative to the directory
	* containing all jars for the requested version of scala. */
	def appRetrievePattern(appID: xsbti.ApplicationID) = appDirectoryName(appID, "/") + "(/[component])/[artifact]-[revision].[ext]"

	/** The name of the directory to retrieve the application and its dependencies to.*/
	def appDirectoryName(appID: xsbti.ApplicationID, sep: String) = appID.groupID + sep + appID.name + sep + appID.version
	/** The name of the directory in the boot directory to put all jars for the given version of scala in.*/
	def baseDirectoryName(scalaVersion: String) = "scala-" + scalaVersion
}
private object ProxyProperties
{
	val HttpProxyEnv = "http_proxy"
	val HttpProxyUser = "http_proxy_user"
	val HttpProxyPassword = "http_proxy_pass"

	val ProxyHost = "http.proxyHost"
	val ProxyPort = "http.proxyPort"
	val ProxyUser = "http.proxyUser"
	val ProxyPassword = "http.proxyPassword"
}