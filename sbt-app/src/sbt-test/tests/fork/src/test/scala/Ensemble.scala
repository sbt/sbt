import java.io.File

import munit.FunSuite

trait Ensemble extends FunSuite:
  def i: Int
  def prefix = System.getProperty("group.prefix")

  test("an ensemble should create all files") {
    val f = new File(prefix + i)
    f.createNewFile
  }
end Ensemble

class Ensemble1 extends Ensemble { def i = 1 }
class Ensemble2 extends Ensemble { def i = 2 }
class Ensemble3 extends Ensemble { def i = 3 }
