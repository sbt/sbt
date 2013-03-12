package sbt

	import complete.{DefaultParsers,Parser}
	import DefaultParsers._
	import Def.ScopedKey
	import Types.idFun
	import java.io.File

object Inspect
{
	sealed trait Mode
	final case class Details(actual: Boolean) extends Mode
	private[this] final class Opt(override val toString: String) extends Mode
	val DependencyTree: Mode = new Opt("tree")
	val Uses: Mode = new Opt("inspect")
	val Definitions: Mode = new Opt("definitions")

	def parser: State => Parser[(Inspect.Mode,ScopedKey[_])] = (s: State) => spacedModeParser(s) flatMap {
		case opt @ (Uses | Definitions) => allKeyParser(s).map(key => (opt, Def.ScopedKey(Global, key)))
		case opt @ (DependencyTree | Details(_)) => spacedKeyParser(s).map(key => (opt, key))
	}
	val spacedModeParser: (State => Parser[Mode]) = (s: State) => {
		val actual = "actual" ^^^ Details(true)
		val tree = "tree" ^^^ DependencyTree
		val uses = "uses" ^^^ Uses
		val related = "related" ^^^ Related
		val definitions = "definitions" ^^^ Definitions
		token(Space ~> (tree | actual | uses | related | definitions)) ?? Details(false)
	}

	def allKeyParser(s: State): Parser[AttributeKey[_]] =
	{
		val keyMap = Project.structure(s).index.keyMap
		token(Space ~> (ID !!! "Expected key" examples keyMap.keySet)) flatMap { key => Act.getKey(keyMap, key, idFun) }
	}
	val spacedKeyParser: State => Parser[ScopedKey[_]] = (s: State) => Act.requireSession(s, token(Space) ~> Act.scopedKeyParser(s))

	def output(s: State, option: Mode, sk: Def.ScopedKey[_]): String =
	{
		val extracted = Project.extract(s)
			import extracted._
		option match
		{
			case Details(actual) =>
				Project.details(structure, actual, sk.scope, sk.key)
			case DependencyTree =>
				val basedir = new File(Project.session(s).current.build)
				Project.settingGraph(structure, basedir, sk).dependsAscii
			case Uses =>
				Project.showUses(Project.usedBy(structure, true, sk.key))
			case Definitions =>
				Project.showDefinitions(sk.key, Project.definitions(structure, true, sk.key))
		}
	}

}
