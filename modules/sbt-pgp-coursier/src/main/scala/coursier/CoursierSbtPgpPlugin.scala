package coursier

import com.typesafe.sbt.pgp.PgpKeys.updatePgpSignatures
import sbt.AutoPlugin

object CoursierSbtPgpPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = com.typesafe.sbt.SbtPgp && coursier.CoursierPlugin

  override val projectSettings = Seq(
    updatePgpSignatures := {
      Tasks.updateTask(
        None,
        withClassifiers = false,
        includeSignatures = true
      ).value
    }
  )

}
