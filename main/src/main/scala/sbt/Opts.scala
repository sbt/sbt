/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah, Indrajit Raychaudhuri
 */
package sbt

import java.io.File
import java.net.URL

/** Options for well-known tasks. */
object Opts {
  object compile {
    val deprecation = "-deprecation"
    def encoding(enc: String) = Seq("-encoding", enc)
    val explaintypes = "-explaintypes"
    val nowarn = "-nowarn"
    val optimise = "-optimise"
    val unchecked = "-unchecked"
    val verbose = "-verbose"
  }
  object doc {
    def generator(g: String): Seq[String] = Seq("-doc-generator", g)
    def sourceUrl(u: String): Seq[String] = Seq("-doc-source-url", u)
    def title(t: String): Seq[String] = Seq("-doc-title", t)
    def version(v: String): Seq[String] = Seq("-doc-version", v)
    def externalAPI(mappings: Iterable[(File, URL)]): Seq[String] = if (mappings.isEmpty) Nil else
      mappings.map { case (f, u) => s"${f.getAbsolutePath}#${u.toExternalForm}" }.mkString("-doc-external-doc:", ",", "") :: Nil
  }
  object resolver {
    import Path._
    val sonatypeReleases = Resolver.sonatypeRepo("releases")
    val sonatypeSnapshots = Resolver.sonatypeRepo("snapshots")
    val sonatypeStaging = new MavenRepository("sonatype-staging", "https://oss.sonatype.org/service/local/staging/deploy/maven2")
    val mavenLocalFile = Resolver.file("Local Repository", userHome / ".m2" / "repository" asFile)
  }
}

object DefaultOptions {
  import Opts._
  import Path._
  import BuildPaths.{ getGlobalBase, getGlobalSettingsDirectory }
  import Project.{ extract, richInitializeTask }
  import Def.Setting

  def javac: Seq[String] = compile.encoding("UTF-8")
  def scalac: Seq[String] = compile.encoding("UTF-8")
  def javadoc(name: String, version: String): Seq[String] = Seq("-doctitle", "%s %s API".format(name, version))
  def scaladoc(name: String, version: String): Seq[String] = doc.title(name) ++ doc.version(version)

  def resolvers(snapshot: Boolean): Seq[Resolver] = {
    if (snapshot) Seq(Classpaths.typesafeSnapshots, resolver.sonatypeSnapshots) else Nil
  }
  def pluginResolvers(plugin: Boolean, snapshot: Boolean): Seq[Resolver] = {
    if (plugin && snapshot) Seq(Classpaths.typesafeSnapshots, Classpaths.sbtPluginSnapshots) else Nil
  }
  def addResolvers: Setting[_] = Keys.resolvers ++= { resolvers(Keys.isSnapshot.value) }

  @deprecated("Use `credentials(State)` instead to make use of configuration path dynamically configured via `Keys.globalSettingsDirectory`; relying on ~/.ivy2 is not recommended anymore.", "0.12.0")
  def credentials: Credentials = Credentials(userHome / ".ivy2" / ".credentials")
  def credentials(state: State): Credentials = Credentials(getGlobalSettingsDirectory(state, getGlobalBase(state)) / ".credentials")
  def addCredentials: Setting[_] = Keys.credentials += { credentials(Keys.state.value) }

  def shellPrompt(version: String): State => String =
    s => "%s:%s:%s> ".format(s.configuration.provider.id.name, extract(s).currentProject.id, version)
  def setupShellPrompt: Setting[_] = Keys.shellPrompt := { shellPrompt(Keys.version.value) }
}
