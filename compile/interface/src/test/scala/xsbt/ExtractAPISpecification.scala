package xsbt

import org.junit.runner.RunWith
import xsbti.api.{ Definition, SourceAPI, ClassLike, Def }
import xsbt.api.SameAPI
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ExtractAPISpecification extends Specification {

  "Existential types in method signatures" should {
    "have stable names" in { stableExistentialNames }
  }

  def stableExistentialNames: Boolean = {
    def compileAndGetFooMethodApi(src: String): Def = {
      val compilerForTesting = new ScalaCompilerForUnitTesting
      val sourceApi = compilerForTesting.extractApiFromSrc(src)
      val FooApi = sourceApi.definitions().find(_.name() == "Foo").get.asInstanceOf[ClassLike]
      val fooMethodApi = FooApi.structure().declared().find(_.name == "foo").get
      fooMethodApi.asInstanceOf[Def]
    }
    val src1 = """
				|class Box[T]
				|class Foo {
				|	def foo: Box[_] = null
				|
				}""".stripMargin
    val fooMethodApi1 = compileAndGetFooMethodApi(src1)
    val src2 = """
				|class Box[T]
				|class Foo {
			    |   def bar: Box[_] = null
				|	def foo: Box[_] = null
				|
				}""".stripMargin
    val fooMethodApi2 = compileAndGetFooMethodApi(src2)
    SameAPI.apply(fooMethodApi1, fooMethodApi2)
  }

  /**
    * Checks if representation of the inherited Namer class (with a declared self variable) in Global.Foo
    * is stable between compiling from source and unpickling. We compare extracted APIs of Global when Global
    * is compiled together with Namers or Namers is compiled first and then Global refers
    * to Namers by unpickling types from class files.
    */
  "Self variable and no self type" in {
    def selectNamer(api: SourceAPI): ClassLike = {
      def selectClass(defs: Iterable[Definition], name: String): ClassLike = defs.collectFirst {
        case cls: ClassLike if cls.name == name => cls
      }.get
      val global = selectClass(api.definitions, "Global")
      val foo = selectClass(global.structure.declared, "Global.Foo")
      selectClass(foo.structure.inherited, "Namers.Namer")
    }
    val src1 =
      """|class Namers {
         |  class Namer { thisNamer => }
         |}
         |""".stripMargin
    val src2 =
      """|class Global {
         |  class Foo extends Namers
         |}
         |""".stripMargin
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val apis = compilerForTesting.extractApisFromSrcs(reuseCompilerInstance = false)(List(src1, src2), List(src2))
    val _ :: src2Api1 :: src2Api2 :: Nil = apis.toList
    val namerApi1 = selectNamer(src2Api1)
    val namerApi2 = selectNamer(src2Api2)
    SameAPI(namerApi1, namerApi2)
  }.pendingUntilFixed("have unstable representation (#2504)")
}
