package sbt  // this API is private[sbt], so only exposed for trusted clients and folks who like breaking.

object ExposeYourself {
	val taskCancelHandler = sbt.Keys.taskCancelHandler
}