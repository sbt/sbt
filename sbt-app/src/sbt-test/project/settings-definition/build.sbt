// Just checking that existing ways of
// setting up projects typechecks

val sharedSettings1 = Seq(
  name := "sharedSettings1"
)

val sharedSettings2 = Seq[Setting[?]](
  name := "sharedSettings2"
)

lazy val root = (project in file("."))

lazy val foo = project
  .settings(sharedSettings1)
  .settings(sharedSettings2)
  .settings (
    name := "pre seq settings",
    sharedSettings1,
    name := "mid seq settings",
    sharedSettings2,
    name := "post seq settings"
  )
