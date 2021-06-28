lazy val foo = taskKey[Unit]("Runs the foo task")

lazy val bar = taskKey[Unit]("Runs the bar task")

def makeFoo(config: Configuration): Setting[_] = 
  foo in config := IO.write(file(s"${config.name}-foo"), "foo")

lazy val PerformanceTest = (config("pt") extend Test)

lazy val root = (
  (project in file("."))
  .configs(PerformanceTest)
  .settings(Seq(Compile, Test, Runtime, PerformanceTest).map(makeFoo) :_*)
  .settings(
     bar in PerformanceTest := IO.write(file("pt-bar"), "bar")
  )
)