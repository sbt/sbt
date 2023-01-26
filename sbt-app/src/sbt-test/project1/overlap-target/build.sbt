lazy val x = project in file("x")

lazy val y = project in file(IO.read(file("ydir")).trim)
