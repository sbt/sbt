/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package lmcoursier.internal

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.{ Document, Element, Node }

/**
 * Minimal parser for Maven POM dependencyManagement section.
 * Used when coursier does not populate Project.dependencyManagement (e.g. BOM POMs).
 */
private[internal] object PomParser {

  private val POM_NS = "http://maven.apache.org/POM/4.0.0"

  /**
   * Extracts (groupId, artifactId, version) from dependencyManagement/dependencies/dependency.
   * Skips dependencies with scope "import" (BOM import) and optional/dependencyManagement imports.
   */
  def dependencyManagement(pomFile: File): Vector[(String, String, String)] = {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(pomFile)
    doc.getDocumentElement.normalize()
    extractDependencyManagement(doc)
  }

  private def extractDependencyManagement(doc: Document): Vector[(String, String, String)] = {
    val dm = findFirstElement(doc.getDocumentElement, "dependencyManagement")
    if (dm == null) return Vector.empty
    val deps = findFirstElement(dm, "dependencies")
    if (deps == null) return Vector.empty
    val result = Vector.newBuilder[(String, String, String)]
    val list = deps.getChildNodes
    var i = 0
    while (i < list.getLength) {
      val node = list.item(i)
      if (node.getNodeType == Node.ELEMENT_NODE && localName(node) == "dependency") {
        val elem = node.asInstanceOf[Element]
        val scope = textOf(elem, "scope")
        val version = textOf(elem, "version")
        // Skip import-scope (BOM import) and entries without version (inherited)
        if (scope != "import" && version != null && version.nonEmpty) {
          val groupId = textOf(elem, "groupId")
          val artifactId = textOf(elem, "artifactId")
          if (groupId != null && groupId.nonEmpty && artifactId != null && artifactId.nonEmpty)
            result += ((groupId.trim, artifactId.trim, version.trim))
        }
      }
      i += 1
    }
    result.result()
  }

  private def findFirstElement(parent: Element, localName: String): Element = {
    val list = parent.getChildNodes
    var i = 0
    while (i < list.getLength) {
      val node = list.item(i)
      if (node.getNodeType == Node.ELEMENT_NODE && this.localName(node) == localName)
        return node.asInstanceOf[Element]
      i += 1
    }
    null
  }

  private def textOf(parent: Element, localName: String): String = {
    val el = findFirstElement(parent, localName)
    if (el == null) null else el.getTextContent
  }

  private def localName(node: Node): String = {
    val name = node.getLocalName
    if (name != null) name else node.getNodeName
  }
}
