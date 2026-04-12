/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.librarymanagement.*
import scala.xml.{ Elem, Node, NodeSeq }

/**
 * Generates Maven POM XML from sbt's own types, without requiring Ivy.
 * This is used by the default publisher when the sbt-ivy plugin is not loaded.
 */
private[sbt] object PomGenerator:

  def makePom(
      mid: ModuleID,
      info: Option[ModuleInfo],
      deps: Vector[ModuleID],
      configurations: Option[Vector[Configuration]],
      extra: NodeSeq,
      scalaModuleInfo: Option[ScalaModuleInfo] = None,
      resolvers: Vector[Resolver] = Vector.empty,
      filterRepositories: MavenRepository => Boolean = _ => true,
      allRepositories: Boolean = false,
  ): Node =
    val crossMid = crossVersionDep(mid, scalaModuleInfo)
    val keepConfs: Set[String] =
      configurations.map(_.map(_.name).toSet).getOrElse(Set.empty)
    val crossVersioned = deps.map(crossVersionDep(_, scalaModuleInfo))
    val filteredDeps =
      if keepConfs.isEmpty then crossVersioned
      else crossVersioned.filter(d => d.configurations.forall(c => confIntersects(c, keepConfs)))

    val (bomDeps, regularDeps) = filteredDeps.partition: d =>
      d.explicitArtifacts.nonEmpty && d.explicitArtifacts.forall(_.`type` == Artifact.PomType)

    <project xmlns="http://maven.apache.org/POM/4.0.0"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
      <modelVersion>4.0.0</modelVersion>
      {makeModuleID(crossMid)}
      {info.map(i => <name>{i.nameFormal}</name>).getOrElse(NodeSeq.Empty)}
      {info.map(makeDescription).getOrElse(NodeSeq.Empty)}
      {info.map(makeHomePage).getOrElse(NodeSeq.Empty)}
      {info.map(makeStartYear).getOrElse(NodeSeq.Empty)}
      {info.map(makeOrganization).getOrElse(NodeSeq.Empty)}
      {info.map(makeScmInfo).getOrElse(NodeSeq.Empty)}
      {info.map(makeDeveloperInfo).getOrElse(NodeSeq.Empty)}
      {info.map(makeLicenses).getOrElse(NodeSeq.Empty)}
      {makeProperties(crossMid)}
      {extra}
      {makeRepositories(resolvers, filterRepositories, allRepositories)}
      {makeDependencyManagement(bomDeps)}
      {makeDependencies(regularDeps)}
    </project>

  private def crossVersionDep(dep: ModuleID, scalaInfo: Option[ScalaModuleInfo]): ModuleID =
    val crossFn = CrossVersion(dep, scalaInfo)
    val crossDep = crossFn match
      case Some(fn) => dep.withName(fn(dep.name)).withCrossVersion(CrossVersion.disabled)
      case None     => dep
    if crossDep.exclusions.isEmpty || scalaInfo.isEmpty then crossDep
    else
      val si = scalaInfo.get
      val crossedExclusions = crossDep.exclusions.map: excl =>
        CrossVersion(excl.crossVersion, si.scalaFullVersion, si.scalaBinaryVersion) match
          case Some(fn) =>
            excl.withName(fn(excl.name)).withCrossVersion(CrossVersion.disabled)
          case None => excl
      crossDep.withExclusions(crossedExclusions)

  private def confIntersects(confStr: String, keepConfs: Set[String]): Boolean =
    confStr
      .split(';')
      .exists: mapping =>
        val from = mapping.split("->").head.trim
        from.split(',').exists(c => keepConfs.contains(c.trim) || c.trim == "*")

  private def makeModuleID(mid: ModuleID): NodeSeq =
    val packaging =
      if mid.explicitArtifacts.isEmpty then "jar"
      else
        val types = mid.explicitArtifacts.map(_.`type`).filterNot(IgnoreTypes)
        if types.isEmpty then Artifact.PomType
        else if types.contains(Artifact.DefaultType) then Artifact.DefaultType
        else types.head
    (<groupId>{mid.organization}</groupId>
     <artifactId>{mid.name}</artifactId>
     <version>{mid.revision}</version>
     <packaging>{packaging}</packaging>)

  private val IgnoreTypes: Set[String] =
    Set(Artifact.SourceType, Artifact.DocType, Artifact.PomType)

  private def makeDescription(info: ModuleInfo): NodeSeq =
    if info.description != null && info.description.nonEmpty then
      <description>{info.description}</description>
    else NodeSeq.Empty

  private def makeHomePage(info: ModuleInfo): NodeSeq =
    info.homepage match
      case Some(h) => <url>{h}</url>
      case _       => NodeSeq.Empty

  private def makeStartYear(info: ModuleInfo): NodeSeq =
    info.startYear match
      case Some(y) => <inceptionYear>{y}</inceptionYear>
      case _       => NodeSeq.Empty

  private def makeOrganization(info: ModuleInfo): NodeSeq =
    <organization>
      <name>{info.organizationName}</name>
      {info.organizationHomepage.map(h => <url>{h}</url>).getOrElse(NodeSeq.Empty)}
    </organization>

  private def makeScmInfo(info: ModuleInfo): NodeSeq =
    info.scmInfo match
      case Some(s) =>
        <scm>
          <url>{s.browseUrl}</url>
          <connection>{s.connection}</connection>
          {
          s.devConnection
            .map(d => <developerConnection>{d}</developerConnection>)
            .getOrElse(NodeSeq.Empty)
        }
        </scm>
      case _ => NodeSeq.Empty

  private def makeDeveloperInfo(info: ModuleInfo): NodeSeq =
    if info.developers.nonEmpty then
      <developers>
        {
        info.developers.map: dev =>
          <developer>
            <id>{dev.id}</id>
            <name>{dev.name}</name>
            <url>{dev.url}</url>
            {
            if dev.email != null && dev.email.nonEmpty then <email>{dev.email}</email>
            else NodeSeq.Empty
          }
          </developer>
      }
      </developers>
    else NodeSeq.Empty

  private def makeLicenses(info: ModuleInfo): NodeSeq =
    if info.licenses.nonEmpty then
      <licenses>
        {
        info.licenses.map: lic =>
          <license>
            <name>{lic.spdxId}</name>
            <url>{lic.uri}</url>
            <distribution>repo</distribution>
          </license>
      }
      </licenses>
    else NodeSeq.Empty

  private def makeProperties(mid: ModuleID): NodeSeq =
    val props = mid.extraAttributes
      .collect:
        case (k, v) if k.startsWith("e:info.") => (k.stripPrefix("e:"), v)
        case (k, v) if k.startsWith("info.")   => (k, v)
    if props.isEmpty then NodeSeq.Empty
    else
      <properties>
        {
        props.toSeq
          .sortBy(_._1)
          .map: (k, v) =>
            Elem(null, k, scala.xml.Null, scala.xml.TopScope, false, scala.xml.Text(v))
      }
      </properties>

  private def makeRepositories(
      resolvers: Vector[Resolver],
      filter: MavenRepository => Boolean,
      allRepositories: Boolean,
  ): NodeSeq =
    val repos = resolvers.collect:
      case r: MavenRepository if r.name != "public" && (allRepositories || filter(r)) =>
        <repository>
          <id>{r.name}</id>
          <name>{r.name}</name>
          <url>{r.root}</url>
        </repository>
    if repos.isEmpty then NodeSeq.Empty
    else <repositories>{repos}</repositories>

  private def makeDependencyManagement(deps: Vector[ModuleID]): NodeSeq =
    if deps.isEmpty then NodeSeq.Empty
    else
      <dependencyManagement>
        <dependencies>
          {
        deps.map: dep =>
          <dependency>
              <groupId>{dep.organization}</groupId>
              <artifactId>{dep.name}</artifactId>
              <version>{dep.revision}</version>
              <type>pom</type>
              <scope>import</scope>
            </dependency>
      }
        </dependencies>
      </dependencyManagement>

  private def makeDependencies(deps: Vector[ModuleID]): NodeSeq =
    if deps.isEmpty then NodeSeq.Empty
    else <dependencies>
        {deps.map(makeDependencyElem)}
      </dependencies>

  private def makeDependencyElem(dep: ModuleID): Elem =
    val (scope, optional) = getScopeAndOptional(dep.configurations)
    val mavenVersion = convertVersion(dep.revision)
    val versionNode: NodeSeq =
      if mavenVersion == null || mavenVersion == "*" || mavenVersion.isEmpty then NodeSeq.Empty
      else <version>{mavenVersion}</version>
    val result: Elem =
      <dependency>
        <groupId>{dep.organization}</groupId>
        <artifactId>{dep.name}</artifactId>
        {versionNode}
        {scopeElem(scope)}
        {optionalElem(optional)}
        {typeAndClassifierElems(dep)}
        {exclusions(dep)}
      </dependency>
    result

  private def getScopeAndOptional(configurations: Option[String]): (Option[String], Boolean) =
    configurations match
      case None => (None, false)
      case Some(confStr) =>
        val confs =
          confStr.split(';').flatMap(_.split("->").head.trim.split(',')).map(_.trim).toSet
        val optional = confs.contains(Configurations.Optional.name)
        val notOptional = confs - Configurations.Optional.name
        val scope = Configurations.defaultMavenConfigurations
          .find(c => notOptional.contains(c.name))
          .map(_.name)
        (scope, optional)

  /** Convert Ivy-style dynamic versions to Maven range format. */
  private def convertVersion(version: String): String =
    if version == null then null
    else if version.endsWith("+") then
      val base = version.stripSuffix("+").stripSuffix(".")
      val parts = base.split('.')
      if parts.nonEmpty then
        parts.last.toIntOption.map(_ + 1) match {
          case Some(last) =>
            val upper = (parts.init :+ last.toString).mkString(".")
            s"[$base,$upper)"
          case None =>
            version
        }
      else version
    else if version == "latest.integration" || version == "latest.release" then ""
    else version

  private def scopeElem(scope: Option[String]): NodeSeq =
    scope match
      case None | Some("compile") => NodeSeq.Empty
      case Some(s)                => <scope>{s}</scope>

  private def optionalElem(opt: Boolean): NodeSeq =
    if opt then <optional>true</optional> else NodeSeq.Empty

  private def typeAndClassifierElems(dep: ModuleID): NodeSeq =
    dep.explicitArtifacts.headOption match
      case None => NodeSeq.Empty
      case Some(art) =>
        val classifier = art.classifier
        val baseType = Option(art.`type`).filter(_ != Artifact.DefaultType)
        val tpe = (classifier, baseType) match
          case (Some(c), Some(t)) if Artifact.classifierType(c) == t => None
          case _                                                     => baseType
        val typeNode = tpe.map(t => <type>{t}</type>).getOrElse(NodeSeq.Empty)
        val classifierNode =
          classifier.map(c => <classifier>{c}</classifier>).getOrElse(NodeSeq.Empty)
        typeNode ++ classifierNode

  private def exclusions(dep: ModuleID): NodeSeq =
    if dep.exclusions.isEmpty then NodeSeq.Empty
    else
      val elems = dep.exclusions.flatMap { excl =>
        val g = excl.organization
        val a = excl.name
        if g.nonEmpty && g != "*" && a.nonEmpty && a != "*" then Some(<exclusion>
            <groupId>{g}</groupId>
            <artifactId>{a}</artifactId>
          </exclusion>)
        else None
      }
      if elems.isEmpty then NodeSeq.Empty
      else <exclusions>{elems}</exclusions>
end PomGenerator
