/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

// Still TODO:
//   inheritance- hierarchy, overriding

import org.scalacheck._
import scala.reflect.Manifest

// specify members
// specify classes
// map members to classes
// compile, instantiate base
// get all vals
// validate vals

object ReflectiveSpecification extends Properties("ReflectUtilities")
{
	import ReflectiveArbitrary._
	// pick other modifiers, any name, any type for the member, any type to find,
	//  pick a class hierarchy, select any class from that hierarchy, add member
	//  instantiate instance, perform reflection and verify it is empty
	specify("Public found", publicFound _)
	specify("Private hidden", privateHidden _)
	
	private def publicFound(isFinal: Boolean, decl: DeclarationType, name: Identifier, rType: ReturnType,
		findType: ReturnType, container: ConcreteContainer) =
	{
		val modifiers: Set[Modifier] = if(isFinal) Set(Final) else Set()
		val member = Member(Public, modifiers, decl, name.toString, rType, true)
		val shouldFind = decl != Def && rType.manifest <:< findType.manifest
		allVals(Map(container -> List(member)), container, findType.manifest).isEmpty == !shouldFind
	}
	private def privateHidden(isFinal: Boolean, decl: DeclarationType, name: Identifier, rType: ReturnType,
		findType: ReturnType, container: ConcreteContainer) =
	{
		val scope = Private(None)
		val modifiers: Set[Modifier] = if(isFinal) Set(Final) else Set()
		val member = Member(scope, modifiers, decl, name.toString, rType, true)
		allVals(Map(container -> List(member)), container, findType.manifest).isEmpty
	}
	private def allVals(classes: Map[Container, List[Member]], check: Container, manifest: Manifest[_]) =
	{
		val instance = ReflectiveCreate.compileInstantiate(classes, check)
		ReflectUtilities.allVals(instance)(manifest)
	}
}

object ReflectiveArbitrary
{
	implicit val arbIdentifier: Arbitrary[Identifier] = Arbitrary { for(id <- Gen.identifier) yield Identifier(id) }
	implicit val arbDeclarationType: Arbitrary[DeclarationType] = Arbitrary { Gen.elements(Val, Def, LazyVal) }
	implicit val arbModifier: Arbitrary[Modifier] = Arbitrary { Gen.elements(Final, Abstract, Override) }
	// TODO: parameterize
	implicit val arbType: Arbitrary[ReturnType] =
		Arbitrary { Gen.elements(classes: _*) }
	implicit val arbConcrete: Arbitrary[ConcreteContainer] = Arbitrary(genConcrete)
	//implicit val arbContainer: Arbitrary[Container] = Arbitrary { Gen.oneOf(arbConcreteContainer, arbTrait) }
	//implicit val arbTrait: Arbitrary[Trait] = Arbitrary {  }
	
	//TODO: inheritance
	def genConcrete = for(name <- Gen.identifier) yield ConcreteClass(name, None, Nil)
	
	val classes = List[ReturnType](
		typ[String]("String", "null"),
		typ[Object]("Object", "null"),
		typ[Int]("Int", "0"),
		typ[List[String]]("List[String]", "Nil"),
		typ[Option[Int]]("Option[Int]", "None") )
	
	def typ[T](name: String, defaultValue: String)(implicit mf: Manifest[T]) =
		BasicReturnType(name, Nil, mf, defaultValue)
}

object ReflectiveCreate
{
	import scala.collection.mutable
	
	def compileInstantiate(classes: Map[Container, List[Member]], instantiate: Container): AnyRef =
	{
		val log = new ConsoleLogger
		log.setLevel(Level.Warn)
		val code = new StringBuilder
		def addMember(m: Member)
		{
			code.append(m.toString)
			code.append("\n")
		}
		def addClass(c: Container, m: List[Member])
		{
			code.append(c.signature)
			code.append(" {\n")
			m.foreach(addMember)
			code.append(" }\n")
		}
		for((c, members) <- classes) addClass(c, members)
		
		val codeString = code.toString
		def doCompileInstantiate(dir: java.io.File): Either[String, AnyRef] =
		{
			val basePath = new ProjectDirectory(dir)
			val source = basePath / "a.scala"
			val sourceFile = source.asFile
			val outputDirectory = basePath / "target"
			for(writeOK <- FileUtilities.write(sourceFile, codeString, log).toLeft("").right;
				compileOK <- (new Compile(100))("reflect", source :: Nil, "", outputDirectory, Nil, log).toLeft("").right)
			yield
			{
				val loader = new java.net.URLClassLoader(Array(outputDirectory.asURL), getClass.getClassLoader)
				val c = Class.forName(instantiate.name, true, loader)
				c.newInstance.asInstanceOf[AnyRef]
			}
		}
		FileUtilities.doInTemporaryDirectory(log)(doCompileInstantiate) match
		{
			case Left(err) => log.error(err); log.error(codeString); throw new RuntimeException(err)
			case Right(x) => x
		}
	}
}

final case class Identifier(override val toString: String) extends NotNull

sealed abstract class Modifier(override val toString: String) extends NotNull
object Final extends Modifier("final")
object Abstract extends Modifier("abstract")
object Override extends Modifier("override")

sealed trait Scope extends NotNull
sealed abstract class QualifiedScope(label: String, qualifier: Option[String]) extends Scope
{
	override def toString = label + qualifier.map("[" + _ + "]").getOrElse("")
}
final case class Private(qualifier: Option[String]) extends QualifiedScope("private", qualifier)
final case class Protected(qualifier: Option[String]) extends QualifiedScope("protected", qualifier)
final object Public extends Scope { override def toString = "" }


sealed abstract class DeclarationType(override val toString: String) extends NotNull
object LazyVal extends DeclarationType("lazy val")
object Val extends DeclarationType("val")
object Def extends DeclarationType("def")

sealed abstract class Container(prefix: String) extends NotNull
{
	def signature: String = prefix + " " + name + parents.map(_.name).mkString(" extends ", " with ", "")
	def name: String
	def mixins: List[Trait]
	def parents: List[Container] = mixins
}
sealed abstract class ClassContainer(prefix: String) extends Container(prefix)
{
	def base: Option[ClassContainer]
	override def parents = base.toList ::: mixins
}
sealed abstract class ConcreteContainer(prefix: String) extends ClassContainer(prefix)
final case class AbstractClass(name: String, base: Option[ClassContainer], mixins: List[Trait]) extends ClassContainer("abstract class")
final case class ConcreteClass(name: String, base: Option[ClassContainer], mixins: List[Trait]) extends ConcreteContainer("class")
final case class Module(name: String, base: Option[ClassContainer], mixins: List[Trait]) extends ConcreteContainer("object")
final case class Trait(name: String, mixins: List[Trait]) extends Container("trait")

trait ReturnType
{
	def name: String
	def parameters: List[ReturnType]
	def manifest: Manifest[_]
	def defaultValue: String
	override def toString = name
}
sealed case class BasicReturnType(name: String, parameters: List[ReturnType],
	manifest: Manifest[_], defaultValue: String) extends ReturnType

case class Member(scope: Scope, modifiers: Set[Modifier], declaration: DeclarationType,
	name: String, mType: ReturnType, valueSpecified: Boolean) extends NotNull
{
	override def toString = scope.toString + modifiers.mkString(" ", " "," ") +
		declaration.toString + " " + name  + " : " + mType.toString +
		(if(valueSpecified) " = " + mType.defaultValue else "")
}