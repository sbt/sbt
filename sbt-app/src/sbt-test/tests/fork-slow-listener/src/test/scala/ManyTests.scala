class ManyTests extends munit.FunSuite:
  (1 to 40).foreach(i => test(s"test $i")(assert(i < 40)))
