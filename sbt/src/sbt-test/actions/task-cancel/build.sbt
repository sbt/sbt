import sbt.ExposeYourself._

taskCancelHandler := { (state: State) =>
  new TaskEvaluationCancelHandler {
    override def start(canceller: TaskCancel): Unit = canceller.cancel()
    override def finish(): Unit = ()
  }
}