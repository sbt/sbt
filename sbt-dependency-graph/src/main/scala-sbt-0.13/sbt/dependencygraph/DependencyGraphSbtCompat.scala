package sbt
package dependencygraph

import scala.language.implicitConversions

object DependencyGraphSbtCompat {
  object Implicits {
    implicit def convertConfig(config: sbt.Configuration): String = config.toString

    implicit class RichUpdateConfiguration(val updateConfig: UpdateConfiguration) extends AnyVal {
      def withMissingOk(missingOk: Boolean): UpdateConfiguration =
        updateConfig.copy(missingOk = missingOk)
    }
  }
}
