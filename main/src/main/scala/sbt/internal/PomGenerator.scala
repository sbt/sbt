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
  ): Node =
    val keepConfs: Set[String] =
      configurations.map(_.map(_.name).toSet).getOrElse(Set.empty)
    val filteredDeps =
      if keepConfs.isEmpty then deps
      else deps.filter(d => d.configurations.forall(c => confIntersects(c, keepConfs)))

    val (bomDeps, regularDeps) = filteredDeps.partition: d =>
      d.explicitArtifacts.nonEmpty && d.explicitArtifacts.forall(_.`type` == Artifact.PomType)

    <project xmlns="http://maven.apache.org/POM/4.0.0"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
      <modelVersion>4.0.0</modelVersion>
      {makeModuleID(mid)}
      {info.map(i => <name>{i.nameFormal}</name>).getOrElse(NodeSeq.Empty)}
      {info.map(makeStartYear).getOrElse(NodeSeq.Empty)}
      {info.map(makeOrganization).getOrElse(NodeSeq.Empty)}
      {info.map(makeScmInfo).getOrElse(NodeSeq.Empty)}
      {info.map(makeDeveloperInfo).getOrElse(NodeSeq.Empty)}
      {info.map(makeLicenses).getOrElse(NodeSeq.Empty)}
      {extra}
      {makeDependencyManagement(bomDeps)}
      {makeDependencies(regularDeps)}
    </project>

  private def confIntersects(confStr: String, keepConfs: Set[String]): Boolean =
    confStr
      .split(';')
      .exists: mapping =>
        val from = mapping.split("->").head.trim
        keepConfs.contains(from) || from == "*"

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
    val versionNode: NodeSeq =
      if dep.revision == null || dep.revision == "*" || dep.revision.isEmpty then NodeSeq.Empty
      else <version>{dep.revision}</version>
    val result: Elem =
      <dependency>
        <groupId>{dep.organization}</groupId>
        <artifactId>{dep.name}</artifactId>
        {versionNode}
        {scopeElem(scope)}
        {optionalElem(optional)}
        {classifierElem(dep)}
        {exclusions(dep)}
      </dependency>
    result

  private def getScopeAndOptional(configurations: Option[String]): (Option[String], Boolean) =
    configurations match
      case None => (None, false)
      case Some(confStr) =>
        val confs = confStr.split(';').map(_.split("->").head.trim).toSet
        val optional = confs.contains(Configurations.Optional.name)
        val notOptional = confs - Configurations.Optional.name
        val scope = Configurations.defaultMavenConfigurations
          .find(c => notOptional.contains(c.name))
          .map(_.name)
        (scope, optional)

  private def scopeElem(scope: Option[String]): NodeSeq =
    scope match
      case None | Some("compile") => NodeSeq.Empty
      case Some(s)                => <scope>{s}</scope>

  private def optionalElem(opt: Boolean): NodeSeq =
    if opt then <optional>true</optional> else NodeSeq.Empty

  private def classifierElem(dep: ModuleID): NodeSeq =
    dep.explicitArtifacts.headOption.flatMap(_.classifier) match
      case Some(c) => <classifier>{c}</classifier>
      case None    => NodeSeq.Empty

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
