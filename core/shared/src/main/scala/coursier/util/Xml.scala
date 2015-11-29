package coursier.util

object Xml {

  /** A representation of an XML node/document, with different implementations on JVM and JS */
  trait Node {
    def label: String
    def child: Seq[Node]
    def isText: Boolean
    def textContent: String
    def isElement: Boolean
  }

  object Node {
    val empty: Node =
      new Node {
        val isText = false
        val isElement = false
        val child = Nil
        val label = ""
        val textContent = ""
      }
  }

}
