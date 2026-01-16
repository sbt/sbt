import scala.annotation.{tailrec, nowarn}
import sbt.util.cacheLevel

@tailrec
def even(x: Int): Boolean = Math.abs(x) match
  case 0 => true
  case 1 => false
  case n => even(n - 2)

@transient val foo = 4

@cacheLevel(include = Array.empty)
lazy val myTask = taskKey[Boolean]("...")

@nowarn
lazy val myProject = project.settings(
  myTask := {
    assert(!file("ran").exists)
    println("running")
    IO.touch(file("ran"))
    even(2)
  }
)
