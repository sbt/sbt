package pkgtest

import sbt._, Keys._

// https://github.com/scala/bug/issues/12112
object PureExpressionPlugin extends AutoPlugin {
  @transient
  lazy val testPureExpression = taskKey[Unit]("")
  override def projectSettings: Seq[Setting[?]] = {
    testPureExpression := {
      updateFull.value
      (Compile / compile).value
      (Test / test).value
    }
  }
}
