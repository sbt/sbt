import Tests._
import Defaults._

val groupSize = 3
val groups = 3

@transient
val check = TaskKey[Unit]("check", "Check all files were created and remove them.")
val munit = "org.scalameta" %% "munit" % "1.0.4"

def groupId(idx: Int) = "group_" + (idx + 1)
def groupPrefix(idx: Int) = groupId(idx) + "_file_"

Global / localCacheDirectory := baseDirectory.value / "diskcache"
scalaVersion := "3.9.0"
organization := "com.example"

lazy val root = rootProject
  .settings(
    Test / testGrouping := Def.uncached {
      val tests = (Test / definedTests).value
      assert(tests.size == 3)
      for idx <- 0 until groups yield
        new Group(
          groupId(idx),
          tests,
          SubProcess(ForkOptions().withRunJVMOptions(Vector("-Dgroup.prefix=" + groupPrefix(idx))))
        )
    },
    check := Def.uncached {
      val files =
        for i <- 0 until groups; j <- 1 to groupSize yield
          file(groupPrefix(i) + j)
      val (exist, absent) = files.partition(_.exists)
      exist.foreach(_.delete())
      if absent.nonEmpty then
        sys.error("Files were not created:\n\t" + absent.mkString("\n\t"))
    },
    concurrentRestrictions := Tags.limit(Tags.ForkedTestGroup, 2) :: Nil,
    libraryDependencies ++= List(
      munit % Test
    )
  )

lazy val core = project
  .settings(
    Test / fork := true,
    libraryDependencies ++= List(
      munit % Test
    ),
    Test / baseDirectory := (LocalRootProject / baseDirectory).value,
  )
