import complete.DefaultParsers._

val check = InputKey[Unit]("check-max-errors")

lazy val root = (project in file("."))
lazy val sub = (project in file("sub")).
  delegateTo(root).
  settings(check <<= checkTask)

lazy val checkTask = InputTask(_ => Space ~> NatBasic) { result =>
  (result, maxErrors) map { (i, max)  =>
    if(i != max) sys.error("Expected max-errors to be " + i + ", but it was " + max)
  }
}
