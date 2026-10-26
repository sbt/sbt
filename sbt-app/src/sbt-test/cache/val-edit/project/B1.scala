import sbt.*, Keys.*

object B1:
  def buildSettings = Seq(
    scalacOptions ++= compilerOptions,
    scalacOptions ++= B2.extraOptions
  )

  val compilerOptions = Seq(
    "-feature"
  )
