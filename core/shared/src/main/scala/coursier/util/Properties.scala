package coursier.util

import java.util.{ Properties => JProperties }

object Properties {

  private lazy val props = {
    val p = new JProperties()
    p.load(
      getClass
        .getClassLoader
        .getResourceAsStream("coursier.properties")
    )
    p
  }

  lazy val version = props.getProperty("version")
  lazy val commitHash = props.getProperty("commit-hash")

}
