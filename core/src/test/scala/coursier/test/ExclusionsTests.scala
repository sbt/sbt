package coursier
package test

import utest._
import core.Resolver.{ exclusionsAdd, exclusionsIntersect }

object ExclusionsTests extends TestSuite {

  val tests = TestSuite {
    val e1 = Set(("org1", "name1"))
    val e2 = Set(("org2", "name2"))

    val enb = Set(("org1", "*"))
    val eob = Set(("*", "name1"))
    val eb = Set(("*", "*"))

    'add{
      'basicZero{
        val result1l = exclusionsAdd(e1, Set.empty)
        val result1r = exclusionsAdd(Set.empty, e1)
        val result2l = exclusionsAdd(e2, Set.empty)
        val result2r = exclusionsAdd(Set.empty, e2)
        assert(result1l == e1)
        assert(result1r == e1)
        assert(result2l == e2)
        assert(result2r == e2)
      }
      'basic{
        val expected = e1 ++ e2
        val result12 = exclusionsAdd(e1, e2)
        val result21 = exclusionsAdd(e2, e1)
        assert(result12 == expected)
        assert(result21 == expected)
      }

      'nameBlob{
        val result1b = exclusionsAdd(e1, enb)
        val resultb1 = exclusionsAdd(enb, e1)
        val result2b = exclusionsAdd(e2, enb)
        val resultb2 = exclusionsAdd(enb, e2)
        assert(result1b == enb)
        assert(resultb1 == enb)
        assert(result2b == (e2 ++ enb))
        assert(resultb2 == (e2 ++ enb))
      }

      'orgBlob{
        val result1b = exclusionsAdd(e1, eob)
        val resultb1 = exclusionsAdd(eob, e1)
        val result2b = exclusionsAdd(e2, eob)
        val resultb2 = exclusionsAdd(eob, e2)
        assert(result1b == eob)
        assert(resultb1 == eob)
        assert(result2b == (e2 ++ eob))
        assert(resultb2 == (e2 ++ eob))
      }

      'blob{
        val result1b = exclusionsAdd(e1, eb)
        val resultb1 = exclusionsAdd(eb, e1)
        val result2b = exclusionsAdd(e2, eb)
        val resultb2 = exclusionsAdd(eb, e2)
        assert(result1b == eb)
        assert(resultb1 == eb)
        assert(result2b == eb)
        assert(resultb2 == eb)
      }
    }

    'intersect{
      'basicZero{
        val result1l = exclusionsIntersect(e1, Set.empty)
        val result1r = exclusionsIntersect(Set.empty, e1)
        val result2l = exclusionsIntersect(e2, Set.empty)
        val result2r = exclusionsIntersect(Set.empty, e2)
        assert(result1l == Set.empty)
        assert(result1r == Set.empty)
        assert(result2l == Set.empty)
        assert(result2r == Set.empty)
      }
      'basic{
        val expected = e1 ++ e2
        val result12 = exclusionsIntersect(e1, e2)
        val result21 = exclusionsIntersect(e2, e1)
        assert(result12 == Set.empty)
        assert(result21 == Set.empty)
      }

      'nameBlob{
        val result1b = exclusionsIntersect(e1, enb)
        val resultb1 = exclusionsIntersect(enb, e1)
        val result2b = exclusionsIntersect(e2, enb)
        val resultb2 = exclusionsIntersect(enb, e2)
        assert(result1b == e1)
        assert(resultb1 == e1)
        assert(result2b == Set.empty)
        assert(resultb2 == Set.empty)
      }

      'orgBlob{
        val result1b = exclusionsIntersect(e1, eob)
        val resultb1 = exclusionsIntersect(eob, e1)
        val result2b = exclusionsIntersect(e2, eob)
        val resultb2 = exclusionsIntersect(eob, e2)
        assert(result1b == e1)
        assert(resultb1 == e1)
        assert(result2b == Set.empty)
        assert(resultb2 == Set.empty)
      }

      'blob{
        val result1b = exclusionsIntersect(e1, eb)
        val resultb1 = exclusionsIntersect(eb, e1)
        val result2b = exclusionsIntersect(e2, eb)
        val resultb2 = exclusionsIntersect(eb, e2)
        assert(result1b == e1)
        assert(resultb1 == e1)
        assert(result2b == e2)
        assert(resultb2 == e2)
      }
    }
  }

}
