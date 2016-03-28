lazy val root = (project in file(".")).
  aggregate(logic, ui)

lazy val logic = (project in file("logic")).
  delegateTo(LocalProject("root"))

lazy val ui = (project in file("ui")).
  delegateTo(LocalProject("root"))
