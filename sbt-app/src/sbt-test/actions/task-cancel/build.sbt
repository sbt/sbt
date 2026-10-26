import sbt.ExposeYourself._

taskCancelStrategy := { (state: State) =>
  new TaskCancellationStrategy {
    type State = Unit
    override def onTaskEngineStart(cancel: () => Unit): Unit = {
      state.currentCommand match {
        case Some(e) if e.commandLine == "loadp" => ()
        case _                                   => cancel()
      }
    }
    override def onTaskEngineFinish(state: State): Unit = ()
  }
}
