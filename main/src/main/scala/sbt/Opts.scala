/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.librarymanagement.{ MavenRepository, Resolver }
import sbt.librarymanagement.ivy.Credentials

import java.io.File
import java.net.URL

import sbt.io.Path
import Path._

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
    def externalAPI(mappings: Iterable[(File, URL)]): Seq[String] =
      if (mappings.isEmpty) Nil
      else
        mappings
          .map { case (f, u) => s"${f.getAbsolutePath}#${u.toExternalForm}" }
          .mkString("-doc-external-doc:", ",", "") :: Nil
  }
  object resolver {
    import sbt.io.syntax._
    val sonatypeReleases = Resolver.sonatypeRepo("releases")
    val sonatypeSnapshots = Resolver.sonatypeRepo("snapshots")
    val sonatypeStaging = MavenRepository(
      "sonatype-staging",
      "https://oss.sonatype.org/service/local/staging/deploy/maven2")
    val mavenLocalFile = Resolver.file("Local Repository", userHome / ".m2" / "repository" asFile)(
      Resolver.defaultPatterns)
    val sbtSnapshots = Resolver.bintrayRepo("sbt", "maven-snapshots")
    val sbtIvySnapshots = Resolver.bintrayIvyRepo("sbt", "ivy-snapshots")
  }
}

object DefaultOptions {
  import Opts._
  import sbt.io.syntax._
  import BuildPaths.{ getGlobalBase, getGlobalSettingsDirectory }
  import Project.extract
  import Def.Setting

  def javac: Seq[String] = compile.encoding("UTF-8")
  def scalac: Seq[String] = compile.encoding("UTF-8")
  def javadoc(name: String, version: String): Seq[String] =
    Seq("-doctitle", "%s %s API".format(name, version))
  def scaladoc(name: String, version: String): Seq[String] =
    doc.title(name) ++ doc.version(version)

  def resolvers(snapshot: Boolean): Vector[Resolver] = {
    if (snapshot) Vector(resolver.sbtSnapshots) else Vector.empty
  }
  def pluginResolvers(plugin: Boolean, snapshot: Boolean): Vector[Resolver] = {
    if (plugin && snapshot) Vector(resolver.sbtSnapshots, resolver.sbtIvySnapshots)
    else Vector.empty
  }
  def addResolvers: Setting[_] = Keys.resolvers ++= { resolvers(Keys.isSnapshot.value) }
  def addPluginResolvers: Setting[_] =
    Keys.resolvers ++= pluginResolvers(Keys.sbtPlugin.value, Keys.isSnapshot.value)

  def credentials(state: State): Credentials =
    Credentials(getGlobalSettingsDirectory(state, getGlobalBase(state)) / ".credentials")
  def addCredentials: Setting[_] = Keys.credentials += { credentials(Keys.state.value) }

  def shellPrompt(version: String): State => String =
    s =>
      "%s:%s:%s> ".format(s.configuration.provider.id.name, extract(s).currentProject.id, version)
  def setupShellPrompt: Setting[_] = Keys.shellPrompt := { shellPrompt(Keys.version.value) }
}
