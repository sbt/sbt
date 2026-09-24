/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.librarymanagement.ModuleID

import scala.xml.{ Node, NodeSeq, PrettyPrinter, XML }

object PomGeneratorTest extends Properties:

  override def tests: List[Test] = List(
    example("preserves sbt plugin metadata through POM formatting", testPluginMetadata),
    example("does not add properties without extra attributes", testNoExtraAttributes),
    example("preserves uncrossed names in legacy-style plugin metadata", testLegacyNames),
  )

  private def testPluginMetadata: Result =
    val module = ModuleID("org.example", "sbt-parent_2.12_1.0", "1.0.0")
      .extra(
        "sbtVersion" -> "1.0",
        "scalaVersion" -> "2.12",
        "info.versionScheme" -> "early-semver",
      )
    val dependencies = Vector(
      pluginDependency("sbt-diamond_2.12_1.0", "0.5.0"),
      pluginDependency("sbt-left_2.12_1.0", "1.+"),
    )
    val formatted = formattedPom(module, dependencies)
    val properties = formatted \ "properties"
    val dependencyAttributes = properties \ "extraDependencyAttributes"
    val lines =
      dependencyAttributes.text.split('\n').iterator.map(_.trim).filter(_.nonEmpty).toVector
    val dynamicDependencyVersion = (formatted \ "dependencies" \ "dependency")
      .find(node => (node \ "artifactId").text == "sbt-left_2.12_1.0")
      .map(node => (node \ "version").text)
    val expectedLines = Vector(
      "+e:sbtVersion:#@#:+1.0:#@#:+module:#@#:+sbt-diamond_2.12_1.0:#@#:" +
        "+e:scalaVersion:#@#:+2.12:#@#:+organisation:#@#:+org.example:#@#:" +
        "+branch:#@#:+@#:NULL:#@:#@#:+revision:#@#:+0.5.0:#@#:",
      "+e:sbtVersion:#@#:+1.0:#@#:+module:#@#:+sbt-left_2.12_1.0:#@#:" +
        "+e:scalaVersion:#@#:+2.12:#@#:+organisation:#@#:+org.example:#@#:" +
        "+branch:#@#:+@#:NULL:#@:#@#:+revision:#@#:+[1,2):#@#:",
    )

    Result.all(
      List(
        Result.assert(properties.size == 1).log(s"properties: $properties"),
        Result.assert((properties \ "sbtVersion").text == "1.0"),
        Result.assert((properties \ "scalaVersion").text == "2.12"),
        Result.assert((properties \ "info.versionScheme").text == "early-semver"),
        Result.assert(dependencyAttributes.size == 1),
        Result.assert(
          dependencyAttributes.head
            .attribute("http://www.w3.org/XML/1998/namespace", "space")
            .exists(_.text == "preserve")
        ),
        Result.assert(lines == expectedLines).log(s"expected: $expectedLines\nobtained: $lines"),
        Result.assert(dynamicDependencyVersion.contains("[1,2)")),
        Result.assert(
          dynamicDependencyVersion.exists: version =>
            lines.exists: line =>
              line.contains("+module:#@#:+sbt-left_2.12_1.0:#@#:") &&
                line.contains(s"+revision:#@#:+$version:#@#:")
        ),
      )
    )
  end testPluginMetadata

  private def testNoExtraAttributes: Result =
    val formatted = formattedPom(
      ModuleID("org.example", "plain-library_3", "1.0.0"),
      Vector(ModuleID("org.example", "dependency_3", "1.0.0")),
    )
    Result.assert((formatted \ "properties").isEmpty)

  private def testLegacyNames: Result =
    val module = ModuleID("org.example", "sbt-parent", "1.0.0")
      .extra("sbtVersion" -> "1.0", "scalaVersion" -> "2.12")
    val formatted = formattedPom(module, Vector(pluginDependency("sbt-child", "1.0.0")))
    val dependencyAttributes = (formatted \ "properties" \ "extraDependencyAttributes").text
    Result
      .assert(dependencyAttributes.contains("+module:#@#:+sbt-child:#@#:"))
      .and(Result.assert(!dependencyAttributes.contains("sbt-child_2.12_1.0")))

  private def pluginDependency(name: String, revision: String): ModuleID =
    ModuleID("org.example", name, revision)
      .extra("sbtVersion" -> "1.0", "scalaVersion" -> "2.12")

  private def formattedPom(module: ModuleID, dependencies: Vector[ModuleID]): Node =
    val pom = PomGenerator.makePom(module, None, dependencies, None, NodeSeq.Empty)
    XML.loadString(new PrettyPrinter(1000, 4).format(pom))

end PomGeneratorTest
