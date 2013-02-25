/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

import xsbti.api._
import scala.util.parsing.{combinator, input}
import combinator.{PackratParsers, RegexParsers}

// TODO: tuples and parentheses for grouping
// not implemented: existential, compound, annotated
object ParseType extends RegexParsers with PackratParsers
{
	type P[I] = PackratParser[I]
	
	def parseType(s: String): Either[String, Type] = parse(s, phrase(tpe))
	
	def parse[T](s: String, p: P[T]): Either[String, T] =
	{
		p(new PackratReader(new input.CharSequenceReader(s.trim, 0))) match
		{
			case Success(t, _) => Right(t)
			case err: NoSuccess =>
			{
				val pos = err.next.pos
				Left("Could not parse command: (" + pos.line + "," + pos.column + "): " + err.msg)
			}
		}
	}

	lazy val tpe: Parser[Type] = functionType | infixType
	
	lazy val reserved = Set("=>", "<-", "#")
	lazy val notReserved = (s: String) => if(reserved(s)) failure(s) else success(s)
	lazy val combine = (ls: List[String]) => ls.mkString("_")
	
	lazy val id = rep1sep(alphaID | symID, "_") >> ( notReserved compose combine )
	lazy val alphaID = """[a-zA-Z0-9]+""".r
	lazy val symID = """[^\s\[\].,\)\(a-zA-Z0-9]+""".r

	lazy val types: P[List[Type]]  =  rep1sep(tpe, ",")

	lazy val typeArgs: P[List[Type]]  =  "[" ~> types <~ "]"
	lazy val tuple: P[Parameterized] = ( "(" ~> types <~ ")" ) ^^ New.tupleType

	lazy val infixType: P[Type] =
		simpleType ~ ((id ~ simpleType)?) ^^ {
			case a ~ None => a
			case a ~ Some(nme ~ b) => new Parameterized( New.fromName(nme), Array(a, b) )
		}

	lazy val simpleType: P[SimpleType] =
		parameterizedType |
		projectionType |
		stableId
		
	lazy val parameterizedType: P[Parameterized] =
		simpleType ~ typeArgs ^^ {
			case base ~ args => new Parameterized( base, args.toArray )
		}
	
	lazy val projectionType: P[Projection] =
		(simpleType <~ "#") ~ id ^^ {
			case prefix ~ select => New.projection(prefix, select)
		}

	lazy val stableId: P[SimpleType] = rep1sep(id, '.') ^^ ( New.toType )
		
	lazy val functionType: P[Parameterized] =
		(functionArgs <~ "=>") ~ tpe ^^ {
			case args ~ result => New.functionType(args, result)
		}
		
	lazy val functionArgs: P[List[Type]] = ("(" ~> rep1sep(infixType, ",") <~ ")") | (tpe ^^ (x => List(x)))
}
object New
{
	lazy val UnitType: SimpleType = fromName("scala.Unit")
	
	def tupleType(args: Seq[Type]): Parameterized = new Parameterized( tupleN(args.length), args.toArray )
	
	def functionType(args: Seq[Type], result: Type): Parameterized  =  new Parameterized( functionN(args.length), (args ++ Seq(result)).toArray )
		
	def functionN(n: Int): SimpleType = fromName("scala.Function" + n)
	def tupleN(n: Int): SimpleType = fromName("scala.Tuple" + n)

	def fromName(s: String): SimpleType = toType(components(s))

	def components(s: String): List[String]  =  expandUnqualified(s).split("\\.").toList

	def toType(p: List[String]): SimpleType  =  if(p.last == Tpe) singleton(p.dropRight(1)) else designator(p)

	def projection(prefix: SimpleType, select: String): Projection =
		new Projection( prefix, select )
	def designator(p: List[String]): Projection =
		p match
		{
			case Nil => sys.error("Empty path")
			case x :: Nil => projection( singleton(Nil), x)
			case xs => projection( singleton(p.dropRight(1)), p.last )
		}

	def singleton(p: List[String])  =  new Singleton( path(p) )

	def path(p: List[String]) = 
	{
		assert(!p.contains(Tpe))
		new Path( p.map(new Id(_)).toArray )
	}

	def expandUnqualified(s: String) = unqualified.getOrElse(s, s)

	lazy val unqualified = (
		expand(immut, "scala.collection.immutable.") ++
		expand(coll, "scala.collection.") ++
		expand(mth, "scala.math.") ++
		expand(jva, "java.lang.") ++
		expand(std, "scala.")
	).toMap

	def expand(x: List[String], base: String) = x.map(x => (x, base + x) )

	lazy val jva = List("String", "Error", "Exception")
	lazy val std = List("Char", "Int", "Double", "Long", "Short", "Byte", "Unit", "Boolean", "Array", "Equiv", "Either", "Option", "Some", "None", "Left", "Right")
	lazy val coll = List("Seq", "Traversable", "Iterable", "Iterator", "IndexedSeq")
	lazy val immut = List("Map", "Set", "List", "Stream", "Range", "Nil", "::")
	lazy val mth = List("Ordering", "Ordered", "BigDecimal", "BigInt", "Numeric")

	lazy val Tpe = "type"
}
