package coursier.util

object WebPage {

  def listElements(url: String, page: String, directories: Boolean): Seq[String] =
    coursier.core.compatibility.listWebPageRawElements(page)
      .collect {
        case elem if elem.nonEmpty && elem.endsWith("/") == directories =>
          elem
            .stripSuffix("/")
            .stripPrefix(url)
            .stripPrefix(":") // bintray typically prepends these
      }
      .filter(n => !n.contains("/") && n != "." && n != "..")

  def listDirectories(url: String, page: String): Seq[String] =
    listElements(url, page, directories = true)

  def listFiles(url: String, page: String): Seq[String] =
    listElements(url, page, directories = false)

}