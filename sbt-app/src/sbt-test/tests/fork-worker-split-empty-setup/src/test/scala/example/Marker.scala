package example

import munit.FunSuite

trait Marker extends FunSuite:
  def n: Int
  test(s"mark $n") { () }

class Test1 extends Marker { def n = 1 }
class Test2 extends Marker { def n = 2 }
class Test3 extends Marker { def n = 3 }
class Test4 extends Marker { def n = 4 }
