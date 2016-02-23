package xsbt.api

import xsbti.api._

import sbt.internal.util.UnitSpec

class NameHashingSpecification extends UnitSpec {

  /**
   * Very basic test which checks whether a name hash is insensitive to
   * definition order (across the whole compilation unit).
   */
  "NameHashing" should "generate hashes that are insensitive to the definition order when adding a new member" in {
    val nameHashing = new NameHashing
    val def1 = new Def(Array.empty, strTpe, Array.empty, "foo", publicAccess, defaultModifiers, Array.empty)
    val def2 = new Def(Array.empty, intTpe, Array.empty, "bar", publicAccess, defaultModifiers, Array.empty)
    val classBar1 = simpleClass("Bar", def1)
    val classBar2 = simpleClass("Bar", def1, def2)
    val api1 = new SourceAPI(Array.empty, Array(classBar1))
    val api2 = new SourceAPI(Array.empty, Array(classBar2))
    val nameHashes1 = nameHashing.nameHashes(api1)
    val nameHashes2 = nameHashing.nameHashes(api2)
    assertNameHashEqualForRegularName("Bar", nameHashes1, nameHashes2)
    assertNameHashEqualForRegularName("foo", nameHashes1, nameHashes2)
    nameHashes1.regularMembers.map(_.name).toSeq should not contain ("bar")
    nameHashes2.regularMembers.map(_.name).toSeq should contain("bar")
  }

  /**
   * Very basic test which checks whether a name hash is insensitive to
   * definition order (across the whole compilation unit).
   */
  it should "generates hashes that are insensitive to the definition order" in {
    val nameHashing = new NameHashing
    val def1 = new Def(Array.empty, intTpe, Array.empty, "bar", publicAccess, defaultModifiers, Array.empty)
    val def2 = new Def(Array.empty, strTpe, Array.empty, "bar", publicAccess, defaultModifiers, Array.empty)
    val nestedBar1 = simpleClass("Bar1", def1)
    val nestedBar2 = simpleClass("Bar2", def2)
    val classA = simpleClass("Foo", nestedBar1, nestedBar2)
    val classB = simpleClass("Foo", nestedBar2, nestedBar1)
    val api1 = new SourceAPI(Array.empty, Array(classA))
    val api2 = new SourceAPI(Array.empty, Array(classB))
    val nameHashes1 = nameHashing.nameHashes(api1)
    val nameHashes2 = nameHashing.nameHashes(api2)
    val def1Hash = HashAPI(def1)
    val def2Hash = HashAPI(def2)
    assert(def1Hash !== def2Hash)
    assert(nameHashes1 === nameHashes2)
    ()
  }

  /**
   * Very basic test which asserts that a name hash is sensitive to definition location.
   *
   * For example, if we have:
   * // Foo1.scala
   * class Foo { def xyz: Int = ... }
   * object Foo
   *
   * and:
   * // Foo2.scala
   * class Foo
   * object Foo { def xyz: Int = ... }
   *
   * then hash for `xyz` name should differ in those two cases
   * because method `xyz` was moved from class to an object.
   */
  it should "generate hashes that are sensitive to the definition location" in {
    val nameHashing = new NameHashing
    val deff = new Def(Array.empty, intTpe, Array.empty, "bar", publicAccess, defaultModifiers, Array.empty)
    val classA = {
      val nestedBar1 = simpleClass("Bar1", deff)
      val nestedBar2 = simpleClass("Bar2")
      simpleClass("Foo", nestedBar1, nestedBar2)
    }
    val classB = {
      val nestedBar1 = simpleClass("Bar1")
      val nestedBar2 = simpleClass("Bar2", deff)
      simpleClass("Foo", nestedBar1, nestedBar2)
    }
    val api1 = new SourceAPI(Array.empty, Array(classA))
    val api2 = new SourceAPI(Array.empty, Array(classB))
    val nameHashes1 = nameHashing.nameHashes(api1)
    val nameHashes2 = nameHashing.nameHashes(api2)
    assert(nameHashes1 !== nameHashes2)
    ()
  }

  /**
   * Test if members introduced in parent class affect hash of a name
   * of a child class.
   *
   * For example, if we have:
   * // Test1.scala
   * class Parent
   * class Child extends Parent
   *
   * and:
   * // Test2.scala
   * class Parent { def bar: Int = ... }
   * class Child extends Parent
   *
   * then hash for `Child` name should be the same in both
   * cases.
   */
  it should "generates hashes that account for definitions in parent class" in {
    val parentA = simpleClass("Parent")
    val barMethod = new Def(Array.empty, intTpe, Array.empty, "bar", publicAccess, defaultModifiers, Array.empty)
    val parentB = simpleClass("Parent", barMethod)
    val childA = {
      val structure = new Structure(lzy(Array[Type](parentA.structure)), lzy(Array.empty[Definition]), lzy(Array.empty[Definition]))
      simpleClass("Child", structure)
    }
    val childB = {
      val structure = new Structure(lzy(Array[Type](parentB.structure)), lzy(Array.empty[Definition]), lzy(Array[Definition](barMethod)))
      simpleClass("Child", structure)
    }
    val parentANameHashes = nameHashesForClass(parentA)
    val parentBNameHashes = nameHashesForClass(parentB)
    assert(Seq("Parent") === parentANameHashes.regularMembers.map(_.name).toSeq)
    assert(Seq("Parent", "bar") === parentBNameHashes.regularMembers.map(_.name).toSeq)
    assert(parentANameHashes !== parentBNameHashes)
    val childANameHashes = nameHashesForClass(childA)
    val childBNameHashes = nameHashesForClass(childB)
    assertNameHashEqualForRegularName("Child", childANameHashes, childBNameHashes)
    ()
  }

  /**
   * Checks if changes to structural types that appear in method signature
   * affect name hash of the method. For example, if we have:
   *
   * // Test1.scala
   * class A {
   * 	def foo: { bar: Int }
   * }
   *
   * // Test2.scala
   * class A {
   *   def foo: { bar: String }
   * }
   *
   * then name hash for "foo" should be different in those two cases.
   */
  it should "generates hashes that account for structural types in definition" in {
    /** def foo: { bar: Int } */
    val fooMethod1 = {
      val barMethod1 = new Def(Array.empty, intTpe, Array.empty, "bar", publicAccess, defaultModifiers, Array.empty)
      new Def(Array.empty, simpleStructure(barMethod1), Array.empty, "foo", publicAccess, defaultModifiers, Array.empty)
    }
    /** def foo: { bar: String } */
    val fooMethod2 = {
      val barMethod2 = new Def(Array.empty, strTpe, Array.empty, "bar", publicAccess, defaultModifiers, Array.empty)
      new Def(Array.empty, simpleStructure(barMethod2), Array.empty, "foo", publicAccess, defaultModifiers, Array.empty)
    }
    val aClass1 = simpleClass("A", fooMethod1)
    val aClass2 = simpleClass("A", fooMethod2)
    val nameHashes1 = nameHashesForClass(aClass1)
    val nameHashes2 = nameHashesForClass(aClass2)
    // note that `bar` does appear here
    assert(Seq("A", "foo", "bar") === nameHashes1.regularMembers.map(_.name).toSeq)
    assert(Seq("A", "foo", "bar") === nameHashes2.regularMembers.map(_.name).toSeq)
    assertNameHashEqualForRegularName("A", nameHashes1, nameHashes2)
    assertNameHashNotEqualForRegularName("foo", nameHashes1, nameHashes2)
    assertNameHashNotEqualForRegularName("bar", nameHashes1, nameHashes2)
  }

  /**
   * Checks that private members are included in the hash of the public API of traits.
   * Including the private members of traits is required because classes that implement a trait
   * have to define the private members of the trait. Therefore, if a private member of a trait is added,
   * modified or removed we need to recompile the classes that implement this trait.
   * For instance, if trait Foo is initially defined as:
   *     trait Foo { private val x = new A }
   * changing it to
   *     trait Foo { private val x = new B }
   * requires us to recompile all implementors of trait Foo, because scalac generates setters and getters
   * for the private fields of trait Foo in its implementor. If the clients of trait Foo are not recompiled,
   * we get abstract method errors at runtime, because the types expected by the setter (for instance) does not
   * match.
   */
  it should "private members in traits" in {
    /* trait Foo { private val x } */
    val fooTrait1 =
      simpleTrait(
        "Foo",
        simpleStructure(new Val(emptyType, "x", privateAccess, defaultModifiers, Array.empty)),
        publicAccess
      )

    /* trait Foo */
    val fooTrait2 =
      simpleTrait(
        "Foo",
        simpleStructure(),
        publicAccess
      )

    val api1 = new SourceAPI(Array.empty, Array(fooTrait1))
    val api2 = new SourceAPI(Array.empty, Array(fooTrait2))

    assert(HashAPI(api1) !== HashAPI(api2))
  }

  /**
   * Checks that private members in non-top-level traits are included as well.
   */
  it should "private members in nested traits" in {
    /* class A { trait Foo { private val x } } */
    val classA1 =
      simpleClass(
        "A",
        simpleTrait(
          "Foo",
          simpleStructure(new Val(emptyType, "x", privateAccess, defaultModifiers, Array.empty)),
          publicAccess
        )
      )

    /* class A { trait Foo } */
    val classA2 =
      simpleClass(
        "A",
        simpleTrait(
          "Foo",
          simpleStructure(),
          publicAccess
        )
      )

    val api1 = new SourceAPI(Array.empty, Array(classA1))
    val api2 = new SourceAPI(Array.empty, Array(classA2))

    assert(HashAPI(api1) !== HashAPI(api2))
  }

  /**
   * Checks that private traits are NOT included in the hash.
   */
  it should "private traits" in {
    /* class Foo { private trait T { private val x } } */
    val classFoo1 =
      simpleClass(
        "Foo",
        simpleTrait(
          "T",
          simpleStructure(new Val(emptyType, "x", privateAccess, defaultModifiers, Array.empty)),
          privateAccess
        )
      )

    /** class Foo { private trait T } */
    val classFoo2 =
      simpleClass(
        "Foo",
        simpleTrait(
          "T",
          simpleStructure(),
          privateAccess
        )
      )

    /** class Foo */
    val classFoo3 =
      simpleClass("Foo")

    val api1 = new SourceAPI(Array.empty, Array(classFoo1))
    val api2 = new SourceAPI(Array.empty, Array(classFoo2))
    val api3 = new SourceAPI(Array.empty, Array(classFoo3))

    assert(HashAPI(api1) === HashAPI(api2) && HashAPI(api2) === HashAPI(api3))
  }

  /**
   * Checks that private members are NOT included in the hash of the public API of classes.
   */
  it should "private members in classes are not included in the api hash" in {
    /* class Foo { private val x } */
    val classFoo1 =
      simpleClass(
        "Foo",
        simpleStructure(new Val(emptyType, "x", privateAccess, defaultModifiers, Array.empty))
      )

    /* class Foo */
    val classFoo2 =
      simpleClass(
        "Foo",
        simpleStructure()
      )

    val api1 = new SourceAPI(Array.empty, Array(classFoo1))
    val api2 = new SourceAPI(Array.empty, Array(classFoo2))

    assert(HashAPI(api1) === HashAPI(api2))
  }

  /**
   * Checks that private members do NOT contribute to name hashes.
   * Test for https://github.com/sbt/sbt/issues/2324
   */
  it should "private members in classes do not contribute to name hashes" in {
    /* class Foo { private val x } */
    val classFoo =
      simpleClass(
        "Foo",
        simpleStructure(new Val(emptyType, "x", privateAccess, defaultModifiers, Array.empty))
      )
    val nameHashes = nameHashesForClass(classFoo)
    // make sure there's no name hash for the private member "x"
    assert(Seq("Foo") === nameHashes.regularMembers.map(_.name).toSeq)
  }

  private def assertNameHashEqualForRegularName(name: String, nameHashes1: _internalOnly_NameHashes,
    nameHashes2: _internalOnly_NameHashes): Unit = {
    val nameHash1 = nameHashForRegularName(nameHashes1, name)
    val nameHash2 = nameHashForRegularName(nameHashes1, name)
    assert(nameHash1 === nameHash2)
  }

  private def assertNameHashNotEqualForRegularName(name: String, nameHashes1: _internalOnly_NameHashes,
    nameHashes2: _internalOnly_NameHashes): Unit = {
    val nameHash1 = nameHashForRegularName(nameHashes1, name)
    val nameHash2 = nameHashForRegularName(nameHashes2, name)
    assert(nameHash1 !== nameHash2)
  }

  private def nameHashForRegularName(nameHashes: _internalOnly_NameHashes, name: String): _internalOnly_NameHash =
    try {
      nameHashes.regularMembers.find(_.name == name).get
    } catch {
      case e: NoSuchElementException => throw new RuntimeException(s"Couldn't find $name in $nameHashes", e)
    }

  private def nameHashesForClass(cl: ClassLike): _internalOnly_NameHashes = {
    val sourceAPI = new SourceAPI(Array.empty, Array(cl))
    val nameHashing = new NameHashing
    nameHashing.nameHashes(sourceAPI)
  }

  private def lzy[T](x: T): Lazy[T] = new Lazy[T] { def get: T = x }

  private def simpleStructure(defs: Definition*) = new Structure(lzy(Array.empty[Type]), lzy(defs.toArray), lzy(Array.empty[Definition]))

  private def simpleClass(name: String, defs: Definition*): ClassLike = {
    val structure = simpleStructure(defs: _*)
    simpleClass(name, structure)
  }

  private def simpleClass(name: String, structure: Structure): ClassLike = {
    new ClassLike(DefinitionType.ClassDef, lzy(emptyType), lzy(structure), Array.empty, Array.empty, name, publicAccess, defaultModifiers, Array.empty)
  }

  private def simpleTrait(name: String, structure: Structure, access: Access): ClassLike = {
    new ClassLike(DefinitionType.Trait, lzy(emptyType), lzy(structure), Array.empty, Array.empty, name, access, defaultModifiers, Array.empty)
  }

  private val emptyType = new EmptyType
  private val intTpe = new Projection(emptyType, "Int")
  private val strTpe = new Projection(emptyType, "String")
  private val publicAccess = new Public
  private val privateAccess = new Private(new Unqualified)
  private val defaultModifiers = new Modifiers(false, false, false, false, false, false, false)

}
