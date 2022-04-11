watchOnIteration := { (count, project, commands) =>
  Watch.CancelWatch
}
watchOnTermination := { (action, count, command, state) =>
  action match {
    case Watch.CancelWatch =>
      java.nio.file.Files.delete(java.nio.file.Paths.get("foo.txt"))
    case Watch.HandleError(e) =>
      if (e.getMessage == "fail")
        java.nio.file.Files.delete(java.nio.file.Paths.get("bar.txt"))
      else
        throw new IllegalStateException("unexpected error")
  }
  state
}
