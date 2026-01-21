/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.librarymanagement.{ Credentials, Resolver }

import java.io.File
import java.net.URI

import sbt.io.Path
import Path.*

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
    def externalAPI(mappings: Iterable[(File, URI)]): Seq[String] =
      if (mappings.isEmpty) Nil
      else
        mappings
          .map { (f, u) => s"${f.getAbsolutePath}#${u.toURL().toExternalForm}" }
          .mkString("-doc-external-doc:", ",", "") :: Nil

    /**
     * Generates Scala 3 scaladoc external mappings option.
     * Format: -external-mappings:regex::[scaladoc3|scaladoc|javadoc]::url,...
     */
    def externalAPIScala3(mappings: Iterable[(File, URI)]): Seq[String] =
      if (mappings.isEmpty) Nil
      else
        mappings
          .map { (f, u) =>
            val fileName = f.getName
            // Escape regex special characters in the filename
            val escapedName = fileName.replaceAll("([\\[\\]{}()*+?.\\\\^$|])", "\\\\$1")
            // Use a regex pattern that matches the file anywhere in the classpath
            val regex = s".*$escapedName"
            // Determine if this is javadoc or scaladoc based on the file/URL
            val docType = if (isJavaDoc(f, u)) "javadoc" else "scaladoc3"
            s"$regex::$docType::${u.toURL().toExternalForm}"
          }
          .mkString("-external-mappings:", ",", "") :: Nil

    private def isJavaDoc(file: File, uri: URI): Boolean = {
      val name = file.getName.toLowerCase
      val url = uri.toString.toLowerCase
      // Heuristics to detect Java documentation
      name.startsWith("rt.jar") ||
      name.contains("java") && !name.contains("scala") ||
      url.contains("docs.oracle.com") ||
      url.contains("javadoc") ||
      url.contains("/java/") && !url.contains("scala")
    }
  }
  object resolver {
    import sbt.io.syntax.*

    val mavenLocalFile = Resolver.file("Local Repository", userHome / ".m2" / "repository")(using
      Resolver.defaultPatterns
    )
  }
}

object DefaultOptions {
  import Opts.*
  import sbt.io.syntax.*
  import BuildPaths.{ getGlobalBase, getGlobalSettingsDirectory }
  import sbt.ProjectExtra.extract
  import Def.Setting

  def javac: Seq[String] = compile.encoding("UTF-8")
  def scalac: Seq[String] = compile.encoding("UTF-8")
  def javadoc(name: String, version: String): Seq[String] =
    Seq("-doctitle", s"${name} ${version} API")
  def scaladoc(name: String, version: String): Seq[String] =
    doc.title(name) ++ doc.version(version)

  def resolvers(snapshot: Boolean): Vector[Resolver] = {
    Vector.empty
  }
  def pluginResolvers(plugin: Boolean, snapshot: Boolean): Vector[Resolver] = {
    if (plugin && snapshot) Vector.empty
    else Vector.empty
  }
  def addResolvers: Setting[?] = Keys.resolvers ++= { resolvers(Keys.isSnapshot.value) }
  def addPluginResolvers: Setting[?] =
    Keys.resolvers ++= pluginResolvers(Keys.sbtPlugin.value, Keys.isSnapshot.value)

  def credentials(state: State): Credentials =
    Credentials(getGlobalSettingsDirectory(state, getGlobalBase(state)) / ".credentials")
  def addCredentials: Setting[?] = Keys.credentials += credentials(Keys.state.value)

  def shellPrompt(version: String): State => String =
    s => s"${s.configuration.provider.id.name}:${Project.extract(s).currentProject.id}:${version}> "
  def setupShellPrompt: Setting[?] = Keys.shellPrompt := { shellPrompt(Keys.version.value) }
}
