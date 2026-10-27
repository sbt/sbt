import sbt.ExposeYourself.*

taskCancelStrategy := { (state: State) =>
  new TaskCancellationStrategy:
    type State = Unit
    override def onTaskEngineStart(canceller: RunningTaskEngine): Unit =
      state.currentCommand match
        case Some(e) if e.commandLine == "loadp" => ()
        case _                                   => canceller.cancelAndShutdown()
    override def onTaskEngineFinish(state: State): Unit = ()
}
