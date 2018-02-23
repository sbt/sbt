package coursier.util

import coursier.core.Versions

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
    def attribute(name: String): Either[String, String] =
      attributesMap.get(name) match {
        case None => Left(s"Missing attribute $name")
        case Some(value) => Right(value)
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

  def text(elem: Node, label: String, description: String): Either[String, String] =
    elem.children
      .find(_.label == label)
      .flatMap(_.children.collectFirst{case Text(t) => t})
      .toRight(s"$description not found")

  def parseDateTime(s: String): Option[Versions.DateTime] =
    if (s.length == 14 && s.forall(_.isDigit))
      Some(Versions.DateTime(
        s.substring(0, 4).toInt,
        s.substring(4, 6).toInt,
        s.substring(6, 8).toInt,
        s.substring(8, 10).toInt,
        s.substring(10, 12).toInt,
        s.substring(12, 14).toInt
      ))
    else
      None

}
