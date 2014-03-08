  import complete.DefaultParsers._

name := {
   // verify lazy vals are handled (#952)
	lazy val x = "sdf"
   x
}

lazy val demo = inputKey[String]("sample")

def parser: complete.Parser[(Int,String)] = token(Space ~> IntBasic <~ Space) ~ token("red")

demo := {
  // verify pattern match on the lhs is handled (#994)
  val (n, s) = parser.parsed
  s * n
}
