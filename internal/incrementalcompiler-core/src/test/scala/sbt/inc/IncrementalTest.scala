// package sbt
// package internal
// package inc

// import xsbt.TestAnalyzingCompiler

// import org.scalatest.FlatSpec

// class IncrementalTest extends FlatSpec {
//   import IncrementalCompilerTest._

//   behavior of "The incremental compiler"

//   it should "handle abstract override members correctly" in abstractOverride()
//   it should "handle abstract types correctly" in abstractType()
//   it should "abstract type override" in abstractTypeOverride()
//   it should "record dependencies on files not included in partial recompilation" in added()
//   it should "support backtick quoted names" in backtickQuotedNames()
//   it should "support by name parameters" in byNameParameters()
//   it should "support default parameters" in defaultParams()
//   it should "detect classfile collisions" in dupClasses()
//   it should "support empty source files" in empty()
//   it should "support empty packages" in emptyPackage()
//   it should "account for erasure" in erasure()
//   it should "behave correctly when there's a dependency on a JAR in <java.home>lib/ext/" in ext()
//   it should "handle mutually dependent sources" in cyclicDependency()
//   it should "handle implicit members" in implicitMembers()
//   it should "handle implicit parameters" in implicitParameters()
//   it should "account for implicit search" in implicitSearch()
//   it should "track dependencies on implicit scope properly" in implicitSearchCompanionScope()
//   it should "track dependencies introduced by imports" in importClass()
//   it should "track dependencies on imports for full packages" in importPackage()
//   it should "consider files that inherit from a macro as NOT defining a macro" in inheritedMacro()
//   it should "remember dependencies in case of intermediate errors" in intermediateError()
//   it should "consider lazyness as part of the public API" in lazyVal()
//   it should "invalidate clients of a macro that has been modified" in macroChangeProvider()
//   it should "track dependencies on macro arguments" in macroArgDeps()
//   it should "track dependencies of macro arguments in nested macro calls" in macroArgDepsNested()
//   it should "consider argument names as part of the public API" in named()
//   it should "detect the introduction of new cyclic dependencies" in newCyclic.pending()
//   it should "consider `override` flag as part of the public API" in overrideMember()
//   it should "invalidate classes whose parents' APIs have changed" in parentChange()
//   it should "invalidate classes when a method in its parent has a different signature" in parentMemberChange()
//   it should "detect qualified access changes" in qualifiedAccess()
//   it should "support source deletion" in deleteSourceA()
//   it should "support source deletion and commenting" in deleteSourceB()
//   it should "support repeated parameters" in repeatedParameters()
//   it should "support resident package objects" in residentPackageObject()
//   it should "detect changes to the used names" in sameFileUsedNames()
//   it should "detect changes to sealed hierarchies" in sealedClasses()
//   it should "support specialization" in specialized()
//   it should "account for stability of names" in stabilityChanges()
//   it should "support structural types" in structuralTypes.pending()
//   it should "support usage of structural types" in structUsage()
//   it should "consider private members of traits in invalidation" in traitPrivateMembers()
//   it should "recompile classes that implement a trait when adding/removing calls to super" in traitSuper()
//   it should "consider transitive member references dependencies" in transitiveMemberRef()
//   it should "consider transitive inheritance dependencies" in transitiveInheritance()
//   it should "consider changes to type alias as changes to the public API" in typeAlias()
//   it should "consider changes the type parameters as members of the public API" in typeParameters()
//   it should "register dependencies on types that are only referenced" in typerefOnly()
//   it should "register dependencies on types that only appear as return types" in typerefReturn()
//   it should "consider the kind of a member as part of the public API" in replaceVar()
//   it should "consider the variance of type parameters as part of the public API" in variance()

//   def compiler = new TestAnalyzingCompiler(xsbti.compile.IncOptionsUtil.defaultIncOptions)

//   def abstractOverride()() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """trait A {
//                        |  def x: Int
//                        |}""".stripMargin,
//         "B.scala" -> """trait B extends A {
//                        |  override def x = 2
//                        |}""".stripMargin,
//         "C.scala" -> """trait C extends A {
//                        |  def x = 5
//                        |}""".stripMargin,
//         "D.scala" -> "trait D extends C with B"
//       ),

//       FailedCompile(
//         "C.scala" -> """trait C extends A {
//                        |  abstract override def x = super.x + 5
//                        |}""".stripMargin
//       )
//     )

//   def abstractType() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """trait A {
//                        |  type S[_]
//                        |}""".stripMargin,
//         "B.scala" -> """trait B extends A {
//                        |  type F = S[Int]
//                        |}""".stripMargin
//       ),

//       FailedCompile(
//         "A.scala" -> """trait A {
//                        |  type S
//                        |}""".stripMargin
//       )
//     )

//   def abstractTypeOverride() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "Bar.scala" -> """object Bar {
//                          |  def bar: Outer.TypeInner = null
//                          |}""".stripMargin,
//         "Foo.scala" -> """object Outer {
//                          |  class Inner { type Xyz }
//                          |  type TypeInner = Inner { type Xyz = Int }
//                          |}""".stripMargin,
//         "Impl.scala" -> """class Impl {
//                           |  def bleep = Bar.bar
//                           |}""".stripMargin
//       ),

//       IncrementalStep(
//         "Bar.scala" -> """object Bar {
//                          |  def bar: Outer.TypeInner = null
//                          |  // comment to trigger recompilation
//                          |}""".stripMargin
//       ) invalidates ()
//     )

//   def added() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """package example
//                        |object A {
//                        |  val x: Int = 3
//                        |}""".stripMargin,
//         "B.scala" -> """package example
//                        |object B {
//                        |  val y: String = "4"
//                        |}""".stripMargin
//       ),

//       FailedCompile(
//         "A.scala" -> """package example
//                        |object A {
//                        |  val x: Int = B.y
//                        |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """package example
//                        |object A {
//                        |  val x: String = B.y
//                        |}""".stripMargin
//       ),

//       FailedCompile(
//         "B.scala" -> """package example
//                        |object B {
//                        |  val y: Int = 5
//                        |}""".stripMargin
//       ),

//       Clean,

//       FailedCompile()
//     )

//   def backtickQuotedNames() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  def `=` = 3
//                        |}""".stripMargin,
//         "B.scala" -> """object B extends App {
//                        |   println(A.`=`)
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |   def asdf = 3
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def byNameParameters() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  def x(i: => String) = ()
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  val x = A.x("3")
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  def x(i: Function0[String]) = ()
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def defaultParams() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  def x(f: String, g: Int): Int = g
//                        |  def x(f: Int, g: Int = 3): Int = g
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  val y = A.x(5)
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  def x(f: String, g: Int = 3): Int = g
//                        |  def x(f: Int, g: Int): Int = g
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def dupClasses() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """package clear
//                        |object A""".stripMargin
//       ),

//       FailedCompile(
//         "B.scala" -> """package clear
//                        |object A""".stripMargin
//       ),

//       IncrementalStep(
//         "B.scala" -> """package clear
//                        |object B""".stripMargin
//       ) invalidates ("A.scala", "B.scala")
//     )

//   def empty() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """package a
//                        |object A {
//                        |  def x = "A"
//                        |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """/*package a
//                        |object A {
//                        |  def x = "A"
//                        |}*/""".stripMargin
//       ),

//       FailedCompile(
//         "B.scala" -> """package a
//                        |class B {
//                        |  def x = A.x
//                        |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """package a
//                        |object A {
//                        |  def x = "A"
//                        |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "B.scala" -> delete,
//         "A.scala" -> """/*package a
//                        |object A {
//                        |  def x = "A"
//                        |}*/""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """package a
//                        |object A {
//                        |  def x = "A"
//                        |}""".stripMargin,
//         "B.scala" -> """package a
//                        |class B {
//                        |  def x = A.x
//                        |}""".stripMargin
//       )
//     )

//   def emptyPackage() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "Define.scala" -> """package a.pkgName
//                             |object Test""".stripMargin,
//         "Use.scala" -> """package a
//                          |import pkgName.Test
//                          |object Use {
//                          |  val x = Test
//                          |}""".stripMargin
//       ),

//       IncrementalStep(
//         "Define.scala" -> """package pkgName
//                             |object Test""".stripMargin
//       ) invalidates "Use.scala",

//       FullCompilation(expectedSteps = 1),

//       FailedCompile(
//         "Define.scala" -> delete
//       ),

//       FullCompilation(
//         expectedSteps = 2,
//         "Define.scala" -> """package a.pkgName
//                             |object Test""".stripMargin
//       )
//     )

//   def erasure() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  def x: List[Int] = List(3)
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  val y: List[Int] = A.x
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  def x: List[String] = List("3")
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def ext() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """import sun.net.spi.nameservice.dns.DNSNameService
//                        |object A {
//                        |  val x = new DNSNameService
//                        |}""".stripMargin
//       ),

//       IncrementalStep() invalidates ()
//     )

//   def cyclicDependency() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  val x = 3
//                        |  val z: Int = B.y
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  val y = A.x
//                        |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  val x = "3"
//                        |  val z: String = B.y
//                        |}""".stripMargin
//       )
//     ).pending

//   def implicitMembers() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """class A {
//                        |  implicit def x(i: Int): String = i.toString
//                        |}""".stripMargin,
//         "B.scala" -> """object B extends A {
//                        |  val x: String = 3
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """class A {
//                        |  def x(i: Int): String = i.toString
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def implicitParameters() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """class A {
//                        |  implicit def e: E = new E
//                        |  def x(i: Int)(implicit y: E): String = ""
//                        |}
//                        |class E""".stripMargin,
//         "B.scala" -> """object B extends A {
//                        |  val y = x(3)
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """class A {
//                        |  implicit def e: E = new E
//                        |  def x(i: Int)(y: E): String = ""
//                        |}
//                        |class E""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def implicitSearch() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> "object A",
//         "B.scala" -> """object B {
//                        |  implicit val x: Ordering[Int] = ???
//                        |}""".stripMargin,
//         "C.scala" -> """object C {
//                        |  import A._, B._
//                        |  implicitly[Ordering[Int]]
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  val x = 1
//                        |}""".stripMargin
//       ) invalidates "C.scala"
//     )

//   def implicitSearchCompanionScope() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """trait A
//                        |object A""".stripMargin,
//         "B.scala" -> "trait B extends A",
//         "M.scala" -> """class M[A](a: A)
//                        |object M {
//                        |  implicit def m[A]: M[A] = ???
//                        |}""".stripMargin,
//         "C.scala" -> """object Test {
//                        |  implicitly[M[B]]
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """trait A
//                        |object A {
//                        |  implicit def m[A]: M[A] = ???
//                        |}""".stripMargin
//       ) invalidates ("B.scala", "C.scala")
//     )

//   def importClass() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """package a
//                        |class A""".stripMargin,
//         "B.scala" -> "import a.A"
//       ),

//       IncrementalStep(
//         "A.scala" -> "package a"
//       ) invalidates "B.scala"
//     )

//   def importPackage() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> "package a.b",
//         "B.scala" -> "import a.b"
//       ),

//       IncrementalStep(
//         "A.scala" -> "package a"
//       ) invalidates "B.scala"
//     ).pending

//   def inheritedMacro() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "Provider.scala" -> """import scala.language.experimental.macros
//                               |import scala.reflect.macros._
//                               |
//                               |abstract class Provider {
//                               |  def notImplementedMacro = macro ???
//                               |}""".stripMargin,
//         "Client.scala" -> """object Client {
//                             |  object RealClient extends Provider
//                             |}""".stripMargin,
//         "Foo.scala" -> """object Foo {
//                          |  val c = Client.RealClient
//                          |}""".stripMargin
//       ),

//       IncrementalStep(
//         "Client.scala" -> """object Client {
//                             |  // Some comment...
//                             |  object RealClient extends Provider
//                             |}""".stripMargin
//       ) invalidates ()
//     )

//   def intermediateError() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  def x = 3
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  val y: Int = A.x
//                        |}""".stripMargin
//       ),

//       FailedCompile(
//         "A.scala" -> """object A {
//                        |  def x: String = 3
//                        |}""".stripMargin
//       ),

//       FailedCompile(
//         "A.scala" -> """object A {
//                        |  def x: String = "3"
//                        |}""".stripMargin
//       )
//     )

//   def lazyVal() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """class A {
//                        |  val x = 3
//                        |}""".stripMargin,
//         "B.scala" -> """class B extends A {
//                        |  override val x = 3
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """class A {
//                        |  lazy val x = 3
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def macroChangeProvider() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "Provider.scala" -> """import scala.language.experimental.macros
//                               |import scala.reflect.macros._
//                               |
//                               |object Provider {
//                               |  def tree(args: Any) = macro treeImpl
//                               |  def treeImpl(c: Context)(args: c.Expr[Any]) = c.universe.reify(args.splice)
//                               |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "Client.scala" -> """object Client {
//                             |  Provider.tree(0)
//                             |}""".stripMargin
//       ),

//       IncrementalStep(
//         "Provider.scala" -> """import scala.language.experimental.macros
//                               |import scala.reflect.macros._
//                               |
//                               |object Provider {
//                               |  def tree(args: Any) = macro treeImpl
//                               |  def treeImpl(c: Context)(args: c.Expr[Any]) = sys.error("no macro for you!")
//                               |}""".stripMargin
//       ) invalidates "Client.scala"
//     )

//   def macroArgDeps() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "Provider.scala" -> """import scala.language.experimental.macros
//                               |import scala.reflect.macros._
//                               |
//                               |object Provider {
//                               |  def printTree(arg: Any) = macro printTreeImpl
//                               |  def printTreeImpl(c: Context)(arg: c.Expr[Any]): c.Expr[String] = {
//                               |    val argStr = arg.tree.toString
//                               |    val literalStr = c.universe.Literal(c.universe.Constant(argStr))
//                               |    c.Expr[String](literalStr)
//                               |  }
//                               |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "Foo.scala" -> """object Foo {
//                          |  def str: String = "abc"
//                          |}""".stripMargin,
//         "Client.scala" -> """object Client {
//                             |  Provider.printTree(Foo.str)
//                             |}""".stripMargin
//       ),

//       IncrementalStep(
//         "Foo.scala" -> "object Foo"
//       ) invalidates "Client.scala"
//     )

//   def macroArgDepsNested() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "Provider.scala" -> """import scala.language.experimental.macros
//                               |import scala.reflect.macros._
//                               |
//                               |object Provider {
//                               |  def printTree(arg: Any) = macro printTreeImpl
//                               |  def printTreeImpl(c: Context)(arg: c.Expr[Any]): c.Expr[String] = {
//                               |    val argStr = arg.tree.toString
//                               |    val literalStr = c.universe.Literal(c.universe.Constant(argStr))
//                               |    c.Expr[String](literalStr)
//                               |  }
//                               |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "Foo.scala" -> """object Foo {
//                          |  def str: String = "abc"
//                          |}""".stripMargin,
//         "Client.scala" -> """object Client {
//                             |  Provider.printTree(Provider.printTree(Foo.str))
//                             |}""".stripMargin
//       ),

//       IncrementalStep(
//         "Foo.scala" -> "object Foo"
//       ) invalidates "Client.scala"
//     )

//   def named() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  def x(zz: Int, yy: Int) = yy - zz
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  A.x(zz = 3, yy = 4)
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  def x(yy: Int, zz: Int) = yy - zz
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def newCyclic() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """trait A { val x = "hello" }""",
//         "B.scala" -> """class B extends A { val y = x }"""
//       ),

//       FailedCompile(
//         "A.scala" -> """trait A { val x = (new B).y }"""
//       )
//     )

//   def overrideMember() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """trait A {
//                        |  def x: Int
//                        |}""".stripMargin,
//         "B.scala" -> """trait B extends A {
//                        |  override def x = 2
//                        |}""".stripMargin,
//         "C.scala" -> """trait C extends A {
//                        |  def x = 5
//                        |}""".stripMargin,
//         "D.scala" -> "trait D extends C with B"
//       ),

//       IncrementalStep(
//         "B.scala" -> """trait B extends A {
//                        |  def x = 2
//                        |}""".stripMargin
//       ) invalidates "D.scala"
//     )

//   def parentChange() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "W.scala" -> """class W {
//                        |  def x = 3
//                        |}
//                        |class V extends W""".stripMargin,
//         "Y.scala" -> """object Y {
//                        |  def main(args: Array[String]) =
//                        |    println( (new Z).x )
//                        |}""".stripMargin,
//         "Z.scala" -> "class Z extends V"
//       ),

//       IncrementalStep(
//         "W.scala" -> """class W {
//                        |  def x = 3
//                        |}
//                        |class V""".stripMargin
//       ) invalidates ("Y.scala", "Z.scala")
//     )

//   def parentMemberChange() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """class A {
//                        |  def x(i: Int) = i+"3"
//                        |}""".stripMargin,
//         "B.scala" -> """class B extends A""",
//         "C.scala" -> """class C extends B {
//                        |  def x(s: String) = s+"5"
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """class A {
//                        |  def x(i: String) = i+"3"
//                        |}""".stripMargin
//       ) invalidates ("B.scala", "C.scala")
//     )

//   def qualifiedAccess() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """package a {
//                        |  package b {
//                        |    object A {
//                        |      private[a] def x = 3
//                        |    }
//                        |  }
//                        |}""".stripMargin,
//         "B.scala" -> """package a
//                        |object B {
//                        |  val y = b.A.x
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """package a.b
//                        |object A {
//                        |  private[b] def x = 3
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def deleteSourceA() =
//     compiler execute Scenario(
//       FailedCompile(
//         "A.scala" -> """package test
//                        |object TestScriptTest {
//                        |  val x: Int = ""
//                        |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> delete
//       )
//     )

//   def deleteSourceB() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """package test
//                        |object A""".stripMargin,
//         "B.scala" -> """package test
//                        |object B""".stripMargin
//       ),

//       FailedCompile(
//         "A.scala" -> """package test
//                        |object A {
//                        |  def test = B.length
//                        |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 1,
//         "B.scala" -> """package test
//                        |object B {
//                        |  def length: Int = 5
//                        |}""".stripMargin
//       ),

//       FailedCompile(
//         "B.scala" -> delete
//       ),

//       FullCompilation(
//         expectedSteps = 2,
//         "B.scala" -> """package test
//                        |object B {
//                        |  def length: Int = 5
//                        |}""".stripMargin
//       ),

//       FailedCompile(
//         "B.scala" -> """package test
//                        |object B""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 2,
//         "B.scala" -> """package test
//                        |object B {
//                        |  def length: Int = 5
//                        |}""".stripMargin
//       ),

//       FailedCompile(
//         "B.scala" -> """/*package test
//                        |object B*/""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 2,
//         "B.scala" -> """package test
//                        |object B {
//                        |  def length: Int = 5
//                        |}""".stripMargin
//       )
//     )

//   def repeatedParameters() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  def x(i: String*) = ()
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  val x = A.x("3")
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  def x(i: Seq[String]) = ()
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def residentPackageObject() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "package.scala" -> """package object example {
//                              |  val green = 3
//                              |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """package example
//                        |object A {
//                        |  val x: Int = green
//                        |}""".stripMargin
//       ),

//       FailedCompile(
//         "package.scala" -> """package object example {
//                              |  val green = "asdf"
//                              |}""".stripMargin
//       ),

//       IncrementalStep(
//         "package.scala" -> """package object example {
//                              |  val green = 3
//                              |}""".stripMargin
//       )
//     )

//   def sameFileUsedNames() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |   def x = 3
//                        |
//                        |   def y = {
//                        |     import B._
//                        |     x
//                        |   }
//                        |}""".stripMargin,
//         "B.scala" -> """object B"""
//       ),

//       IncrementalStep(
//         "B.scala" -> """object B {
//                        |  def x = 3
//                        |}""".stripMargin
//       ) invalidates "A.scala"
//     )

//   def sealedClasses() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """sealed trait A
//                        |class B extends A
//                        |class C extends A""".stripMargin,
//         "D.scala" -> """object D {
//                        |def x(a: A) =
//                        |  a match {
//                        |    case _: B => ()
//                        |    case _: C => ()
//                        |  }
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """sealed trait A
//                        |class B extends A
//                        |class C extends A
//                        |class E extends A""".stripMargin
//       ) invalidates "D.scala"
//     )

//   def specialized() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """class A {
//                        |  def x[T](t: T) = t
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  val a = new A
//                        |  a.x(3)
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """class A {
//                        |  def x[@specialized T](t: T) = t
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def stabilityChanges() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  val x = new C
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  import A.x.y
//                        |  val z = y
//                        |}""".stripMargin,
//         "C.scala" -> """class C {
//                        |  val y = 4
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  def x = new C
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def structuralTypes() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  def x: Int = 3
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  def onX(m: { def x: Int } ) =
//                        |    m.x
//                        |}""".stripMargin,
//         "C.scala" -> """object C {
//                        |  B.onX(A)
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  def x: Byte = 3
//                        |}""".stripMargin
//       ) invalidates "C.scala"
//     )

//   def structUsage() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  def x: { def q: Int } = sys.error("not important")
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  val y: Int = A.x.q
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  def x: { def q: String } = sys.error("not important")
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def traitPrivateMembers() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "Bar.scala" -> """trait Bar {
//                          |  private val x = new A
//                          |}""".stripMargin,
//         "Classes.scala" -> """class A
//                              |class B""".stripMargin,
//         "Foo.scala" -> "class Foo extends Bar"
//       ),

//       IncrementalStep(
//         "Bar.scala" -> """trait Bar {
//                          |  private val x = new B
//                          |}""".stripMargin
//       ) invalidates ("Foo.scala")
//     )

//   def traitSuper() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """trait A {
//                        |  def x: Int
//                        |}
//                        |class E extends A {
//                        |  def x = 19
//                        |}""".stripMargin,
//         "B.scala" -> """trait B extends A {
//                        |  abstract override def x = 1
//                        |}
//                        |trait C extends A {
//                        |  abstract override def x = 3
//                        |}""".stripMargin,
//         "X.scala" -> """class X extends E with C with B"""
//       ),

//       FullCompilation(
//         expectedSteps = 2,
//         "B.scala" -> """trait B extends A {
//                        |  abstract override def x = super.x + 2
//                        |}
//                        |trait C extends A {
//                        |  abstract override def x = 3
//                        |}""".stripMargin
//       ),

//       FullCompilation(
//         expectedSteps = 2,
//         "B.scala" -> """trait B extends A {
//                        |  abstract override def x = super.x + 2
//                        |}
//                        |trait C extends A {
//                        |  abstract override def x = super.x
//                        |}""".stripMargin
//       )
//     )

//   def transitiveMemberRef() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  val x = "a"
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  val y = A.x
//                        |}""".stripMargin,
//         "C.scala" -> """object C {
//                        |  val z = B.y.length
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  val x = 5
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def transitiveInheritance() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """trait A {
//                        |  val x = "a"
//                        |}""".stripMargin,
//         "B.scala" -> """trait B extends A""",
//         "C.scala" -> """trait C extends B {
//                        |  val z = x.length
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """trait A {
//                        |  val x = 5
//                        |}""".stripMargin
//       ) invalidates ("B.scala", "C.scala")
//     )

//   def typeAlias() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  type X = Option[Int]
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  def y: A.X = Option(3)
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  type X = Int
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def typeParameters() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """trait A[T]""",
//         "B.scala" -> """trait B[T] extends A[T]""",
//         "C.scala" -> """object C {
//                        |  new A[Int] {}
//                        |}""".stripMargin,
//         "D.scala" -> """object D {
//                        |  def x[T](a: A[T]) = a
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """trait A"""
//       ) invalidates ("B.scala", "C.scala", "D.scala")
//     )

//   def typerefOnly() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """class A[T]
//                        |abstract class C {
//                        |  def foo: A[B]
//                        |}""".stripMargin,
//         "B.scala" -> """class B"""
//       ),

//       IncrementalStep(
//         "B.scala" -> delete
//       ) invalidates "A.scala"
//     )

//   def typerefReturn() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """object A {
//                        |  type I = Int
//                        |  def x: I = sys.error("not important")
//                        |}""".stripMargin,
//         "B.scala" -> """object B {
//                        |  val y: Int = A.x
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """object A {
//                        |  type I = String
//                        |  def x: I = sys.error("Not important")
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def replaceVar() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """class A {
//                        |  def x = 3
//                        |  def x_=(x$1: Int) = ()
//                        |}""".stripMargin,
//         "B.scala" -> """class B extends A {
//                        |  override var x = 3
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """class A {
//                        |  var x = 3
//                        |}""".stripMargin
//       ) invalidates "B.scala"
//     )

//   def variance() =
//     compiler execute Scenario(
//       FullCompilation(
//         expectedSteps = 1,
//         "A.scala" -> """class A[+T]""",
//         "B.scala" -> """object B {
//                        |  val a: A[Any] = new A[Int]
//                        |}""".stripMargin
//       ),

//       IncrementalStep(
//         "A.scala" -> """class A[T]"""
//       ) invalidates "B.scala"
//     )

// }
