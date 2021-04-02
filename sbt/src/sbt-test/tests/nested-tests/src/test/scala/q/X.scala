package q

//
// On 1.0.3+ this test will say:
// [info] + Nesting.startsWith: OK, passed 100 tests.
// [info] Passed: Total 1, Failed 0, Errors 0, Passed 1
//
// On 1.0.0 to 1.0.2 it will crash with:
// [error] java.lang.ClassNotFoundException: q.X.Y$
//

import org.scalacheck.{Prop, Properties}
import Prop.forAll

class U extends Properties("Nesting")
object X extends U {
    property("startsWith") = forAll { (a: String, b: String) =>
    (a+b).startsWith(a)
  }
  object Y extends U {
    property("endsWith") = forAll { (a: String, b: String) =>
      (a+b).endsWith(b)
    }
  }
}
