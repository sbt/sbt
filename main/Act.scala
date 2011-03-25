/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt

	import Project.ScopedKey
	import Keys.{sessionSettings, thisProject}
	import Load.BuildStructure
	import complete.{DefaultParsers, Parser}
	import DefaultParsers._
	import java.net.URI

object Act
{
	val GlobalString = "*"

	// this does not take aggregation into account
	def scopedKey(index: KeyIndex, current: ProjectRef, defaultConfigs: ProjectRef => Seq[String], keyMap: Map[String, AttributeKey[_]]): Parser[ScopedKey[_]] =
	{
		for {
			proj <- optProjectRef(index, current)
			confAmb <- config( index configs proj )
			keyConf <- key(index, proj, configs(confAmb, defaultConfigs, proj), keyMap)
			taskExtra <- taskExtrasParser(keyMap, IMap.empty) }
		yield {
			val (key, conf) = keyConf
			val (task, extra) = taskExtra
			ScopedKey( Scope( Select(proj), toAxis(conf map ConfigKey.apply, Global), task, extra), key )
		}
	}
	def examplesStrict(p: Parser[String], exs: Set[String]): Parser[String] =
		p examples exs filter exs
		
	def optionalAxis[T](p: Parser[T], ifNone: ScopeAxis[T]): Parser[ScopeAxis[T]] =
		p.? map { opt => toAxis(opt, ifNone) }
	def toAxis[T](opt: Option[T], ifNone: ScopeAxis[T]): ScopeAxis[T] =
		opt match { case Some(t) => Select(t); case None => ifNone }
	def defaultConfigs(data: Settings[Scope])(project: ProjectRef): Seq[String] =
		thisProject in project get data map( _.configurations.map(_.name)) getOrElse Nil

	def config(confs: Set[String]): Parser[Option[String]] =
		token( (examplesStrict(ID, confs) | GlobalString) <~ ':' ).?

	def configs(explicit: Option[String], defaultConfigs: ProjectRef => Seq[String], proj: ProjectRef): List[Option[String]] =
		explicit match
		{
			case None => None :: defaultConfigs(proj).map(c => Some(c)).toList
			case Some(GlobalString) =>  None :: Nil
			case Some(_) => explicit :: Nil
		}

	def key(index: KeyIndex, proj: ProjectRef, confs: Seq[Option[String]], keyMap: Map[String,AttributeKey[_]]): Parser[(AttributeKey[_], Option[String])] =
	{
		val confMap = confs map { conf => (conf, index.keys(proj, conf)) } toMap;
		val allKeys = (Set.empty[String] /: confMap.values)(_ ++ _)
		token(ID examples allKeys).flatMap { keyString =>
			val conf = confMap.flatMap { case (key, value) => if(value contains keyString) key :: Nil else Nil } headOption;
			getKey(keyMap, keyString, k => (k, conf.flatMap(identity)))
		}
	}
	def getKey[T](keyMap: Map[String,AttributeKey[_]], keyString: String, f: AttributeKey[_] => T): Parser[T] =
		keyMap.get(keyString) match {
			case Some(k) => success(f(k))
			case None => failure("Invalid key: " + keyString)
		}

	val spacedComma = token(OptSpace ~ ',' ~ OptSpace)

	def taskExtrasParser(knownKeys: Map[String, AttributeKey[_]], knownValues: IMap[AttributeKey, Set]): Parser[(ScopeAxis[AttributeKey[_]], ScopeAxis[AttributeMap])] =
	{
		val extras = extrasParser(knownKeys, knownValues)
		val taskAndExtra = 
			optionalAxis(taskAxisParser(knownKeys), Global) flatMap { taskAxis =>
				if(taskAxis.isSelect)
					optionalAxis(spacedComma ~> extras, Global) map { x => (taskAxis, x) }
				else
					extras map { x => (taskAxis, Select(x)) }
			}
		val base = token('(') ~> taskAndExtra <~ token(')')
		base ?? ( (Global, Global) )
	}

	def taskAxisParser(knownKeys: Map[String, AttributeKey[_]]): Parser[AttributeKey[_]] =
		token("for" ~ Space) ~> knownIDParser(knownKeys)

	def extrasParser(knownKeys: Map[String, AttributeKey[_]], knownValues: IMap[AttributeKey, Set]): Parser[AttributeMap] =
	{
		val validKeys = knownKeys.filter { case (_, key) => knownValues get key exists(!_.isEmpty) }
		if(validKeys.isEmpty)
			failure("")
		else
			rep1sep( extraParser(validKeys, knownValues), spacedComma) map AttributeMap.apply
	}

	def extraParser(knownKeys: Map[String, AttributeKey[_]], knownValues: IMap[AttributeKey, Set]): Parser[AttributeEntry[_]] =
	{
		val keyp = knownIDParser(knownKeys) <~ token(':' ~ OptSpace)
		keyp flatMap { case key: AttributeKey[t] =>
			val valueMap: Map[String,t] = knownValues(key).map( v => (v.toString, v)).toMap
			knownIDParser(valueMap) map { value => AttributeEntry(key, value) }
		}
	}
	def knownIDParser[T](knownKeys: Map[String, T]): Parser[T] =
		 token(examplesStrict(ID, knownKeys.keys.toSet)) map knownKeys

	def projectRef(index: KeyIndex, currentBuild: URI): Parser[ProjectRef] =
	{
		val uris = index.buildURIs
		val build = token( '(' ~> Uri(uris).map(uri => Scope.resolveBuild(currentBuild, uri)) <~ ')') ?? currentBuild
		def projectID(uri: URI) = token( examplesStrict(ID, index projects uri) <~ '/' )

		for(uri <- build; id <- projectID(uri)) yield
			ProjectRef(uri, id)
	}
	def optProjectRef(index: KeyIndex, current: ProjectRef) =
		projectRef(index, current.build) ?? current

	def actParser(s: State): Parser[() => State] = requireSession(s, actParser0(s))

	private[this] def actParser0(state: State) =
	{
		val extracted = Project extract state
		showParser.flatMap { show =>
			scopedKeyParser(extracted) flatMap Aggregation.valueParser(state, extracted.structure, show)
		}
	}
	def showParser = token( ("show" ~ Space) ^^^ true) ?? false
	def scopedKeyParser(state: State): Parser[ScopedKey[_]] = scopedKeyParser(Project extract state)
	def scopedKeyParser(extracted: Extracted): Parser[ScopedKey[_]] =
	{
		import extracted._
		val defaultConfs = (ref: ProjectRef) => if(Project.getProject(ref, structure).isDefined) defaultConfigs(structure.data)(ref) else Nil
		scopedKey(structure.index.keyIndex, currentRef, defaultConfs, structure.index.keyMap)
	}


	def requireSession[T](s: State, p: => Parser[T]): Parser[T] =
		if(s get sessionSettings isEmpty) failure("No project loaded") else p
}