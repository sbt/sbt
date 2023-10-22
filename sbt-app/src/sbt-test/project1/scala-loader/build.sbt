lazy val checkLoaders = taskKey[Unit]("")

def checkTask = subs.map(sub => scalaInstance in LocalProject(sub.id)).join.map { sis =>
  assert(sis.sliding(2).forall{ case Seq(x,y) => x.loader == y.loader }, "Not all ScalaInstances had the same class loader.")
}

lazy val root = (project in file(".")).
  settings(
    checkLoaders := checkTask.value,
    concurrentRestrictions := Nil
  )

lazy val x1 = newProject(1)
lazy val x2 = newProject(2)
lazy val x3 = newProject(3)
lazy val x4 = newProject(4)
lazy val x5 = newProject(5)
lazy val x6 = newProject(6)
lazy val x7 = newProject(7)
lazy val x8 = newProject(8)
lazy val x9 = newProject(9)
lazy val x10 = newProject(10)
lazy val x11 = newProject(11)
lazy val x12 = newProject(12)
lazy val x13 = newProject(13)
lazy val x14 = newProject(14)
lazy val x15 = newProject(15)
lazy val x16 = newProject(16)
lazy val x17 = newProject(17)
lazy val x18 = newProject(18)
lazy val x19 = newProject(19)
lazy val x20 = newProject(20)

lazy val subs = Seq(x1, x2, x3, x4, x5, x6, x7, x8, x9, x10,
  x11, x12, x13, x14, x15, x16, x17, x18, x19, x20)

def newProject(i: Int): Project =
  Project("x" + i.toString, file(i.toString)).settings(
    scalaVersion := "2.9.2" // this should be a Scala version different from the one sbt uses
  )
