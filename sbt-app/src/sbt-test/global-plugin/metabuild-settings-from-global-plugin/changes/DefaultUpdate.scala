package ga

import sbt.*, Keys.*

object DefaultUpdate extends AutoPlugin:
  override def trigger = allRequirements
  override def projectSettings = Seq(
    globalPluginUpdate := Def.uncached(
      UpdateReport(file("default-update"), Vector.empty, UpdateStats(0, 0, 0, false), Map.empty)
    )
  )
