import sbt.ExposeYourself._

taskCancelHandler := { (state: State) =>
  new TaskEvaluationCancelHandler {
    type State = Unit
    override def onTaskEngineStart(canceller: TaskCancel): Unit = canceller.cancel()
    override def finish(result: Unit): Unit = ()
  }
}