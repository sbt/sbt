package coursier.util

object TestEscape {

  private val unsafeChars: Set[Char] = " %$&+,:;=?@<>#".toSet

  // Scala version of http://stackoverflow.com/questions/4571346/how-to-encode-url-to-avoid-special-characters-in-java/4605848#4605848
  // '/' was removed from the unsafe character list
  private def escape(input: String): String = {

    def toHex(ch: Int) =
      (if (ch < 10) '0' + ch else 'A' + ch - 10).toChar

    def isUnsafe(ch: Char) =
      ch > 128 || ch < 0 || unsafeChars(ch)

    input.flatMap {
      case ch if isUnsafe(ch) =>
        "%" + toHex(ch / 16) + toHex(ch % 16)
      case other =>
        other.toString
    }
  }

  def urlAsPath(url: String): String = {

    assert(!url.startsWith("file:/"), s"Got file URL: $url")

    url.split(":", 2) match {
      case Array(protocol, remaining) =>
        val remaining0 =
          if (remaining.startsWith("///"))
            remaining.stripPrefix("///")
          else if (remaining.startsWith("/"))
            remaining.stripPrefix("/")
          else
            throw new Exception(s"URL $url doesn't contain an absolute path")

        val remaining1 =
          if (remaining0.endsWith("/"))
            // keeping directory content in .directory files
            remaining0 + ".directory"
          else
            remaining0

        escape(protocol + "/" + remaining1.dropWhile(_ == '/'))

      case _ =>
        throw new Exception(s"No protocol found in URL $url")
    }
  }

}
