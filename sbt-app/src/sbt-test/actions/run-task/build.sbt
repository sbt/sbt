val demo = taskKey[Unit]("Demo run task")
fullRunTask(demo, Compile, "A", "1", "1")
demo / fork := true
demo / javaOptions := "-Dsbt.check.forked=true" :: Nil

val demoIn = InputKey[Unit]("demoIn", "Demo run input task", demo)
fullRunInputTask(demoIn, Compile, "A", "1")
