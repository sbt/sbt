package coursier.util

import scalaz.{\/-, -\/, \/, Scalaz}

object Xml {

  /** A representation of an XML node/document, with different implementations on JVM and JS */
  trait Node {
    def label: String
    /** Namespace / key / value */
    def attributes: Seq[(String, String, String)]
    def children: Seq[Node]
    def isText: Boolean
    def textContent: String
    def isElement: Boolean

    def attributesFromNamespace(namespace: String): Seq[(String, String)] =
      attributes.collect {
        case (`namespace`, k, v) =>
          k -> v
      }

    lazy val attributesMap = attributes.map { case (_, k, v) => k -> v }.toMap
    def attribute(name: String): String \/ String =
      attributesMap.get(name) match {
        case None => -\/(s"Missing attribute $name")
        case Some(value) => \/-(value)
      }
  }

  object Node {
    val empty: Node =
      new Node {
        val isText = false
        val isElement = false
        val children = Nil
        val label = ""
        val attributes = Nil
        val textContent = ""
      }
  }

  object Text {
    def unapply(n: Node): Option[String] =
      if (n.isText) Some(n.textContent)
      else None
  }

  def text(elem: Node, label: String, description: String) = {
    import Scalaz.ToOptionOpsFromOption

    elem.children
      .find(_.label == label)
      .flatMap(_.children.collectFirst{case Text(t) => t})
      .toRightDisjunction(s"$description not found")
  }

}
