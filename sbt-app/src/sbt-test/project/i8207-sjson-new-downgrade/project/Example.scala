import sbt.*
import sbt.Keys.*

object Example {
  lazy val settings = Seq(javaOptions += "-Dfoo=bar")
}
