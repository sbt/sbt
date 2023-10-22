lazy val explicit = "explicit"

lazy val check = taskKey[Unit]("check")
lazy val checkEvery = taskKey[Unit]("check every")
lazy val customA = taskKey[String]("custom A")
lazy val customB = taskKey[String]("custom B")
lazy val customC = taskKey[String]("custom C")
lazy val customD = taskKey[String]("custom D")
lazy val customE = taskKey[String]("custom E")
lazy val globalDepE = taskKey[String]("globally defined dependency of E")
lazy val projectDepE = taskKey[String]("per-project dependency of E")

(Global / organization) := "org.example"

(Global / version) := "1.0"

(Global / customC) := "base"

(Global / name) := "global-name"

(Global / globalDepE) := "globalE"

// ---------------- Derived settings

// verify that deriving is transitive
inScope(GlobalScope)(Seq(
  Def.derive(customA := customB.value + "-a"),
  Def.derive(customB := thisProject.value.id + "-b"),
  // verify that a setting with multiple triggers still only gets added once
  Def.derive(customC := s"${organization.value}-${customC.value}-${version.value}"),
  // verify that the scope can be filtered
  //  in this case, only scopes for a project are enabled
  Def.derive(customD := name.value, filter = _.project.isSelect),
  // verify that a setting with multiple triggers is only added when all are present
  //  depE is defined globally, but description is defined per-project
  //  if customE were added in Global because of name, there would be an error
  //  because description wouldn't be found
  Def.derive(customE := globalDepE.value + "-" + projectDepE.value)
))

// ---------------- Projects

lazy val a = project.settings(
  projectDepE := "A"
)

lazy val b = project.settings(
  // verify that an explicit setting has precedence over a derived setting in the same scope
  customB := {
   System.err.println("customB explicit initialization.")
   explicit
  },
  projectDepE := "B"
)


// ---------------- Verification

def same[T](x: T, y: T): Unit = {
  assert(x == y, s"Actual: '$x', Expected: '$y'")
}

check := {
  val aa = (a / customA).value
  same(aa, "a-b-a")
  val bb = (b / customB).value
  same(bb, explicit)
  val ac = (a / customC).value
  // TODO - Setting with multiple triggers is no longer added just once...
  //same(ac, "org.example-base-1.0")
  val globalD = (Global / customD).?.value
  same(globalD, None)
  val aD = (a / customD).value
  val bD = (b / customD).value
  same(aD, "a")
  same(bD, "b")
  val globalE = (Global / customE).?.value
  same(globalE, None)
  val aE = (a / customE).value
  val bE = (b / customE).value
  same(aE, "globalE-A")
  same(bE, "globalE-B")
}

checkEvery := {
  val aD = (a / customD).value
  same(aD, "every")
  val gD = (b / customD).value
  same(gD, "every")
}
