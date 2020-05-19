package coursier

import com.typesafe.sbt.pgp.PgpKeys.updatePgpSignatures
import coursier.sbtcoursier.UpdateTasks
import sbt.AutoPlugin

object CoursierSbtPgpPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = com.typesafe.sbt.SbtPgp && coursier.sbtcoursier.CoursierPlugin

  override val projectSettings = Seq(
    updatePgpSignatures := UpdateTasks.updateTask(withClassifiers = false, includeSignatures = true).value
  )

}
