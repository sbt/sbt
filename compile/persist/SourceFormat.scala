/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

	import xsbti.SafeLazy
	import xsbti.api._
	import sbt.Using
	import sbinary._
	import DefaultProtocol._
	import Operations.{read,write}
	import JavaIO._
	import java.io.File
	import scala.collection.mutable

object SourceFormat extends Format[Source]
{
	import java.io._
	def reads(in: Input): Source =
	{
		val oin = new ObjectInputStream(new InputWrapperStream(in))
		try { oin.readObject.asInstanceOf[Source] } finally { oin.close() }
	}
	def writes(out: Output, src: Source)
	{
		val oout = new ObjectOutputStream(new OutputWrapperStream(out))
		try { oout.writeObject(src) } finally { oout.close() }
	}
}
final class InputWrapperStream(in: Input) extends java.io.InputStream
{
	def toInt(b: Byte) = if(b < 0) b + 256 else b.toInt
	def read() = try { toInt(in.readByte)  } catch { case e: sbinary.EOF => -1 }
	override def read(b: Array[Byte], off: Int, len: Int) = in.readTo(b, off, len)
}
final class OutputWrapperStream(out: Output) extends java.io.OutputStream
{
	override def write(bs: Array[Byte], off: Int, len: Int) = out.writeAll(bs, off, len)
	def write(b: Int) = out.writeByte(b.toByte)
}

object SourceFormat2 extends Format[Source]
{
	private[this] final val StructureFields = 3
	private[this] final val ClassFields = 2

	private[this]def toMap[T](a: Seq[T]): Map[T, Int] = a.zipWithIndex.toMap

	def reads(in: Input): Source =
	{
		var pending: List[Lazy[_]] = Nil
		def forcePending() = pending foreach { _.get }
			
		def lzyC[T <: AnyRef](i: Int, off: Int): Lazy[T] = lzy { getData[T](i, off, classData, ClassFields) }
		def lzyS[T <: AnyRef](i: Int, off: Int): Lazy[T] = lzy { getData[T](i, off, structureData, StructureFields) }
		def getData[T <: AnyRef](i: Int, off: Int, backing: Array[AnyRef], fields: Int): T = backing(i*fields+off).asInstanceOf[T]
		def lzy[T <: AnyRef](t: => T): Lazy[T] =
		{
			val z = SafeLazy(t)
			pending ::= z
			z
		}

		val structureCount = read[Int](in)

		lazy val refs: References = new References {
			def structure(id: Int) = structures(id)
			def classLike(id: Int) = classes(id)
			def id(s: Structure) = noID()
			def id(s: ClassLike) = noID()
			def noID() = error("References only for reading")
		}
		lazy val formats = new DefaultAPIFormats()(refs)
		import formats._

		lazy val classesStrict = read[Array[(DefinitionType, Array[TypeParameter], String, Access, Modifiers, Array[Annotation])]](in)

		// shells for types that can be cyclic.  The actual contents are filled in after the shells are constructed so that
		//   the contents can have cyclic references
		lazy val structures = Array.tabulate(structureCount)(i => new xsbti.api.Structure( lzyS(i,0), lzyS(i,1), lzyS(i,2) ) )
		lazy val classes = Array.tabulate(classesStrict.size) { i =>
			val c = classesStrict(i)
			new xsbti.api.ClassLike( c._1, lzyC(i,0), lzyC(i,1), c._2, c._3, c._4, c._5, c._6 )
		}

		def readFlat[T <: Product](implicit r: Reads[Array[T]]): Array[AnyRef] =
			read[Array[T]](in)(r).flatMap(_.productIterator.asInstanceOf[Iterator[AnyRef]].toTraversable)

		lazy val structureData = readFlat[(Array[Type], Array[Definition], Array[Definition])]
		lazy val classData = readFlat[(Type, Structure)]

		// order is important here, we are forcing in the correct order
		classesStrict;  // read the strict contents of ClassLikes
		structures;  // force the Structure shells to be constructed
		classes; // force the ClassLike shells to be constructed, filling in the strict parts now and deferring the lazy parts
		structureData; // force loading the lazy contents of Structures
		classData; // force loading the lazy contents of ClassLikes

		forcePending() // force the shells to be filled in
		read[Source](in)
	}

	def writes(out: Output, s: Source)
	{
		val (structures, classes) = getStructures(s)
		val structuresMap = toMap(structures)
		val classesMap = toMap(classes)
		implicit val references = new References {
			def id(s: Structure) = structuresMap(s)
			def id(s: ClassLike) = classesMap(s)
			def noID() = error("References only for writing")
			def structure(id: Int) = noID()
			def classLike(id: Int) = noID()
		}
		val formats = new DefaultAPIFormats
		import formats._
		
		val expandedStructures = structures map { s => (s.parents, s.declared, s.inherited)}
			// TODO: type parameters and annotations might need to be lazy as well
		val expandedClassesStrict = classes map { c => (c.definitionType, c.typeParameters, c.name, c.access, c.modifiers, c.annotations) }
		val expandedClassesLazy = classes map { c => (c.selfType, c.structure) }

		write(out, structures.size )
		write(out, expandedClassesStrict)
		write(out, expandedStructures)
		write(out, expandedClassesLazy)
		write(out, s)
	}

	def getStructures(s: Source): (Array[Structure], Array[ClassLike]) =
	{
		val structures = new mutable.HashSet[Structure]
		val classes = new mutable.HashSet[ClassLike]
		val search = new Visit {
			override def visitStructure0(s: Structure)
			{
				structures += s
				super.visitStructure0(s)
			}
			override def visitClass0(c: ClassLike)
			{
				classes += c
				super.visitClass0(c: ClassLike)
			}
		}
		search.visit(s)
		(structures.toArray, classes.toArray)
	}
}

trait References {
	def id(s: Structure): Int
	def id(s: ClassLike): Int
	def structure(id: Int): Structure
	def classLike(id: Int): ClassLike
}