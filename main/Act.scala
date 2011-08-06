/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt

	import Project.{ScopedKey, showContextKey}
	import Keys.{sessionSettings, thisProject}
	import Load.BuildStructure
	import complete.{DefaultParsers, Parser}
	import DefaultParsers._
	import Types.idFun
	import java.net.URI

object Act
{
	val GlobalString = "*"

	// this does not take aggregation into account
	def scopedKey(index: KeyIndex, current: ProjectRef, defaultConfigs: Option[ResolvedReference] => Seq[String], keyMap: Map[String, AttributeKey[_]]): Parser[ScopedKey[_]] =
	{
		for {
			proj <- optProjectRef(index, current)
			confAmb <- config( index configs proj )
			keyConfs <- key(index, proj, configs(confAmb, defaultConfigs, proj), keyMap)
			keyConfTaskExtra <- {
				val andTaskExtra = (key: AttributeKey[_], conf: Option[String]) => 
					taskExtrasParser(index.tasks(proj, conf, key.label), keyMap, IMap.empty) map { case (task, extra) => (key, conf, task, extra) }
				oneOf(keyConfs map { _ flatMap andTaskExtra.tupled })
			}
		} yield {
			val (key, conf, task, extra) = keyConfTaskExtra
			ScopedKey( Scope( toAxis(proj, Global), toAxis(conf map ConfigKey.apply, Global), task, extra), key )
		}
	}
	def examples(p: Parser[String], exs: Set[String], label: String): Parser[String] =
		p !!! ("Expected " + label) examples exs
	def examplesStrict(p: Parser[String], exs: Set[String], label: String): Parser[String] =
		filterStrings(examples(p, exs, label), exs, label)
		
	def optionalAxis[T](p: Parser[T], ifNone: ScopeAxis[T]): Parser[ScopeAxis[T]] =
		p.? map { opt => toAxis(opt, ifNone) }
	def toAxis[T](opt: Option[T], ifNone: ScopeAxis[T]): ScopeAxis[T] =
		opt match { case Some(t) => Select(t); case None => ifNone }
	def defaultConfigs(data: Settings[Scope])(project: ProjectRef): Seq[String] =
		thisProject in project get data map( _.configurations.map(_.name)) getOrElse Nil

	def config(confs: Set[String]): Parser[Option[String]] =
		token( (examplesStrict(ID, confs, "configuration") | GlobalString) <~ ':' ).?

	def configs(explicit: Option[String], defaultConfigs: Option[ResolvedReference] => Seq[String], proj: Option[ResolvedReference]): List[Option[String]] =
		explicit match
		{
			case None => None :: defaultConfigs(proj).map(c => Some(c)).toList
			case Some(GlobalString) =>  None :: Nil
			case Some(_) => explicit :: Nil
		}

	def key(index: KeyIndex, proj: Option[ResolvedReference], confs: Seq[Option[String]], keyMap: Map[String,AttributeKey[_]]): Parser[Seq[Parser[(AttributeKey[_], Option[String])]]] =
	{
		val confMap = confs map { conf => (conf, index.keys(proj, conf)) } toMap;
		val allKeys = (Set.empty[String] /: confMap.values)(_ ++ _)
		token(ID !!! "Expected key" examples allKeys).map { keyString =>
			val valid = confs.filter{ conf => confMap(conf) contains keyString }
			def get(conf: Option[String]) = getKey(keyMap, keyString, k => (k, conf))
			val parsers = valid map { conf => getKey(keyMap, keyString, k => (k, conf)) }
			if(parsers.isEmpty) get(None) :: Nil else parsers
		}
	}
	def getKey[T](keyMap: Map[String,AttributeKey[_]], keyString: String, f: AttributeKey[_] => T): Parser[T] =
		keyMap.get(keyString) match {
			case Some(k) => success(f(k))
			case None => failure(Command.invalidValue("key", keyMap.keys)(keyString))
		}

	val spacedComma = token(OptSpace ~ ',' ~ OptSpace)

	def taskExtrasParser(tasks: Set[AttributeKey[_]], knownKeys: Map[String, AttributeKey[_]], knownValues: IMap[AttributeKey, Set]): Parser[(ScopeAxis[AttributeKey[_]], ScopeAxis[AttributeMap])] =
	{
		val extras = extrasParser(knownKeys, knownValues)
		val taskParser = optionalAxis(taskAxisParser(tasks, knownKeys), Global)
		val taskAndExtra = 
			taskParser flatMap { taskAxis =>
				if(taskAxis.isSelect)
					optionalAxis(spacedComma ~> extras, Global) map { x => (taskAxis, x) }
				else
					extras map { x => (taskAxis, Select(x)) }
			}
		val base = token('(', hide = tasks.isEmpty) ~> taskAndExtra <~ token(')')
		base ?? ( (Global, Global) )
	}

	def taskAxisParser(tasks: Set[AttributeKey[_]], allKnown: Map[String, AttributeKey[_]]): Parser[AttributeKey[_]] =
	{
		val knownKeys: Map[String, AttributeKey[_]] = tasks.toSeq.map(key => (key.label, key)).toMap
		val known = allKnown ++ knownKeys
		val valid = known.keys.toSet
		val suggested = knownKeys.keys.toSet
		val keyP = filterStrings(examples(ID, suggested, "key"), valid, "key") map known
		token("for" ~ Space) ~> token(keyP)
	}
	def filterStrings(base: Parser[String], valid: Set[String], label: String): Parser[String] =
		base.filter(valid, Command.invalidValue(label, valid))

	def extrasParser(knownKeys: Map[String, AttributeKey[_]], knownValues: IMap[AttributeKey, Set]): Parser[AttributeMap] =
	{
		val validKeys = knownKeys.filter { case (_, key) => knownValues get key exists(!_.isEmpty) }
		if(validKeys.isEmpty)
			failure("No valid extra keys.")
		else
			rep1sep( extraParser(validKeys, knownValues), spacedComma) map AttributeMap.apply
	}

	def extraParser(knownKeys: Map[String, AttributeKey[_]], knownValues: IMap[AttributeKey, Set]): Parser[AttributeEntry[_]] =
	{
		val keyp = knownIDParser(knownKeys, "Not a valid extra key") <~ token(':' ~ OptSpace)
		keyp flatMap { case key: AttributeKey[t] =>
			val valueMap: Map[String,t] = knownValues(key).map( v => (v.toString, v)).toMap
			knownIDParser(valueMap, "extra value") map { value => AttributeEntry(key, value) }
		}
	}
	def knownIDParser[T](knownKeys: Map[String, T], label: String): Parser[T] =
		 token(examplesStrict(ID, knownKeys.keys.toSet, label)) map knownKeys

	def projectRef(index: KeyIndex, currentBuild: URI): Parser[Option[ResolvedReference]] =
	{
		val global = token(GlobalString <~ '/') ^^^ None
		global | some(resolvedReference(index, currentBuild, '/'))
	}
	def resolvedReference(index: KeyIndex, currentBuild: URI, trailing: Parser[_]): Parser[ResolvedReference] =
	{
		def projectID(uri: URI) = token( examplesStrict(ID, index projects uri, "project ID") <~ trailing )
		def projectRef(uri: URI) = projectID(uri) map { id => ProjectRef(uri, id) }

		val uris = index.buildURIs
		val resolvedURI = Uri(uris).map(uri => Scope.resolveBuild(currentBuild, uri))
		val buildRef = token( '{' ~> resolvedURI <~ '}' ).?

		buildRef flatMap {
			case None => projectRef(currentBuild)
			case Some(uri) => projectRef(uri) | token(trailing ~> success(BuildRef(uri)))
		}
	}
	def optProjectRef(index: KeyIndex, current: ProjectRef): Parser[Option[ResolvedReference]] =
		projectRef(index, current.build) ?? Some(current)

	def actParser(s: State): Parser[() => State] = requireSession(s, actParser0(s))

	private[this] def actParser0(state: State) =
	{
		val extracted = Project extract state
		import extracted.{showKey, structure}
		showParser.flatMap { show =>
			scopedKeyParser(extracted) flatMap Aggregation.valueParser(state, structure, show)
		}
	}
	def showParser = token( ("show" ~ Space) ^^^ true) ?? false
	def scopedKeyParser(state: State): Parser[ScopedKey[_]] = scopedKeyParser(Project extract state)
	def scopedKeyParser(extracted: Extracted): Parser[ScopedKey[_]] =
	{
		import extracted._
		def confs(uri: URI) = if(structure.units.contains(uri)) defaultConfigs(structure.data)(ProjectRef(uri, rootProject(uri))) else Nil
		val defaultConfs: Option[ResolvedReference] => Seq[String] = {
			case None => confs(structure.root)
			case Some(BuildRef(uri)) => confs(uri)
			case Some(ref: ProjectRef) => if(Project.getProject(ref, structure).isDefined) defaultConfigs(structure.data)(ref) else Nil
		}
		scopedKey(structure.index.keyIndex, currentRef, defaultConfs, structure.index.keyMap)
	}

	def requireSession[T](s: State, p: => Parser[T]): Parser[T] =
		if(s get sessionSettings isEmpty) failure("No project loaded") else p
}