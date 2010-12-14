package sbt.parse

	import Parser._

final class PropertyParser[A,B](propertyNames: ((Option[A], Option[B])) => Set[String], currentScope: (Option[A], Option[B]),
	aNames: Set[String], bNames: Set[String],
	resolveA: String => A, resolveB: String => B)
{
	val wsc = charClass(_.isWhitespace)
	val ws = ( wsc + ) examples(" ")
	// lesson #1: never end a 'token' with a nullable Parser or the next token will never be suggested.
	// lesson #2 where there is whitespace (including required whitespace), it should start a token
	//  might be issues to try to address better at some point.  These should now be fixed, but the comment will be left here for now.
	val optWs = ( wsc * ) examples("")

	val nameChar = (c: Char) => idChar(c) && !(c == ',' || c == '*')
	val idChar = (c: Char) => !(c.isWhitespace || c == ']' || c == '[')

	val Wildcard = '*' ^^^ None
	def named[T](resolve: String => T, exs: Set[String]) = word(nameChar, exs) map { w => Some(resolve(w.mkString)) }
	def key[T](resolve: String => T, exs: Set[String]) = token(optWs ~> (Wildcard | named(resolve, exs)))
	val projectKey = key(resolveA, aNames)
	val taskKey = key(resolveB, bNames)

	def ch(c: Char) = token(optWs ~ c)
	def scopeBase = (ch('[') ~> projectKey) ~ ( ch(',') ~> taskKey <~ ch(']') )
	def scope = scopeBase ?? currentScope

	def word(valid: Char => Boolean, exs: Set[String]) = ( charClass(valid) +).string examples exs
	def id(exs: Set[String]) = word(idChar, exs)
	def value = token( any.+.string, "<value>" )
	def propertyKey(sc: (Option[A], Option[B])) = token(id(propertyNames(sc)) map { (sc, _) } )
	def base(command: String) = (token(command) ~> scope <~ token(ws)) flatMap propertyKey

	val setBase = base("set") ~ (token(ws) ~> value)
	val getBase = base("get")

	val setParser: Parser[SetConfig[A,B]]  =  setBase map { case scopeKey ~ key ~ value => new SetConfig(scopeKey, key, value) }
	val getParser: Parser[GetConfig[A,B]]  =  getBase map { case scopeKey ~ key => new GetConfig(scopeKey, key) }
}
final class SetConfig[A,B](val scope: (Option[A], Option[B]), val propertyName: String, val propertyValue: String)
final class GetConfig[A,B](val scope: (Option[A], Option[B]), val propertyName: String)