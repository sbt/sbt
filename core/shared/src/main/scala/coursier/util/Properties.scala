package coursier.util

import java.util.Properties

object Properties {

  private lazy val props = {
    val p = new Properties()
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
