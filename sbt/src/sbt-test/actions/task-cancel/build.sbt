import sbt.ExposeYourself._

taskCancelStrategy := { (state: State) =>
  new TaskCancellationStrategy {
    type State = Unit
    override def onTaskEngineStart(canceller: RunningTaskEngine): Unit = canceller.cancelAndShutdown()
    override def onTaskEngineFinish(state: State): Unit = ()
  }
}
