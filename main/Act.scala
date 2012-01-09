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
	import CommandSupport.ShowCommand

object Act
{
	val GlobalString = "*"

	// this does not take aggregation into account
	def scopedKey(index: KeyIndex, current: ProjectRef, defaultConfigs: Option[ResolvedReference] => Seq[String],
		keyMap: Map[String, AttributeKey[_]], data: Settings[Scope]): Parser[ScopedKey[_]] =
	{
		implicit val show = Project.showRelativeKey(current, index.buildURIs.size > 1)
		def taskKeyExtra(proj: Option[ResolvedReference], confAmb: ParsedAxis[String]): Seq[Parser[ScopedKey[_]]] =
			for {
				conf <- configs(confAmb, defaultConfigs, proj, index)
			} yield for {
				task <- taskAxis(conf, index.tasks(proj, conf), keyMap) map resolveTask
				key <- key(index, proj, conf, task, keyMap, data)
				extra <- extraAxis(keyMap, IMap.empty)
			} yield
				makeScopedKey( proj, conf, task, extra, key )

		for {
			proj <- optProjectRef(index, current)
			confAmb <- config( index configs proj )
			result <- select(taskKeyExtra(proj, confAmb), data)
		} yield
			result
	}
	def makeScopedKey(proj: Option[ResolvedReference], conf: Option[String], task: Option[AttributeKey[_]], extra: ScopeAxis[AttributeMap], key: AttributeKey[_]): ScopedKey[_] =
		ScopedKey( Scope( toAxis(proj, Global), toAxis(conf map ConfigKey.apply, Global), toAxis(task, Global), extra), key )

	def select(allKeys: Seq[Parser[ScopedKey[_]]], data: Settings[Scope])(implicit show: Show[ScopedKey[_]]): Parser[ScopedKey[_]] =
		seq(allKeys) flatMap { ss =>
			val default = ss.headOption match {
				case None => noValidKeys
				case Some(x) => success(x)
			}
			selectFromValid(ss filter isValid(data), default)
		}
	def selectFromValid(ss: Seq[ScopedKey[_]], default: Parser[ScopedKey[_]])(implicit show: Show[ScopedKey[_]]): Parser[ScopedKey[_]] =
		selectByTask(selectByConfig(ss)) match
		{
			case Seq() => default
			case Seq(single) => success(single)
			case multi => failure("Ambiguous keys: " + showAmbiguous(multi))
		}
	def selectByConfig(ss: Seq[ScopedKey[_]]): Seq[ScopedKey[_]] =
		ss match
		{
			case Seq() => Nil
			case Seq(x, tail @ _*) => // select the first configuration containing a valid key
				tail.takeWhile(_.scope.config == x.scope.config) match
				{
					case Seq() => x :: Nil
					case xs => x +: xs
				}
		}
	def selectByTask(ss: Seq[ScopedKey[_]]): Seq[ScopedKey[_]] =
	{
		val (selects, globals) = ss.partition(_.scope.task.isSelect)
		if(globals.nonEmpty) globals else selects
	}
			
	def noValidKeys = failure("No such key.")

	def showAmbiguous(keys: Seq[ScopedKey[_]])(implicit show: Show[ScopedKey[_]]): String =
		keys.take(3).map(x => show(x)).mkString("", ", ", if(keys.size > 3) ", ..." else "")
	
	def isValid(data: Settings[Scope])(key: ScopedKey[_]): Boolean =
		data.definingScope(key.scope, key.key) == Some(key.scope)
	
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

	def config(confs: Set[String]): Parser[ParsedAxis[String]] =
		token( (GlobalString ^^^ ParsedGlobal | value(examples(ID, confs, "configuration")) ) <~ ':' ) ?? Omitted

	def configs(explicit: ParsedAxis[String], defaultConfigs: Option[ResolvedReference] => Seq[String], proj: Option[ResolvedReference], index: KeyIndex): Seq[Option[String]] =
		explicit match
		{
			case Omitted => None +: defaultConfigs(proj).flatMap(nonEmptyConfig(index, proj))
			case ParsedGlobal =>  None :: Nil
			case pv: ParsedValue[String] => Some(pv.value) :: Nil
		}
	def nonEmptyConfig(index: KeyIndex, proj: Option[ResolvedReference]): String => Seq[Option[String]] = config =>
		if(index.isEmpty(proj, Some(config))) Nil else Some(config) :: Nil

	def key(index: KeyIndex, proj: Option[ResolvedReference], conf: Option[String], task: Option[AttributeKey[_]], keyMap: Map[String,AttributeKey[_]], data: Settings[Scope]):
		Parser[AttributeKey[_]] =
	{
		def keyParser(keys: Set[String]): Parser[AttributeKey[_]] =
			token(ID !!! "Expected key" examples keys) flatMap { keyString=>
				getKey(keyMap, keyString, idFun)
			}
		keyParser(index.keys(proj, conf, task))
	}

	def getKey[T](keyMap: Map[String,AttributeKey[_]], keyString: String, f: AttributeKey[_] => T): Parser[T] =
		keyMap.get(keyString) match {
			case Some(k) => success(f(k))
			case None => failure(Command.invalidValue("key", keyMap.keys)(keyString))
		}

	val spacedComma = token(OptSpace ~ ',' ~ OptSpace)

	def extraAxis(knownKeys: Map[String, AttributeKey[_]], knownValues: IMap[AttributeKey, Set]): Parser[ScopeAxis[AttributeMap]] =
	{
		val extrasP = extrasParser(knownKeys, knownValues)
		val extras = token('(', hide = _ == 1 && knownValues.isEmpty) ~> extrasP <~ token(')')
		optionalAxis(extras, Global)
	}

	def taskAxis(d: Option[String], tasks: Set[AttributeKey[_]], allKnown: Map[String, AttributeKey[_]]): Parser[ParsedAxis[AttributeKey[_]]] =
	{
		val knownKeys: Map[String, AttributeKey[_]] = tasks.toSeq.map(key => (key.label, key)).toMap
		val valid = allKnown ++ knownKeys
		val suggested = knownKeys.keySet
		val keyP = filterStrings(examples(ID, suggested, "key"), valid.keySet, "key") map valid
		(token(value(keyP) | GlobalString ^^^ ParsedGlobal ) <~ token("::".id) ) ?? Omitted
	}
	def resolveTask(task: ParsedAxis[AttributeKey[_]]): Option[AttributeKey[_]] =
		task match
		{
			case ParsedGlobal | Omitted => None
			case t: ParsedValue[AttributeKey[_]] => Some(t.value)
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
		val global = token(GlobalString ~ '/') ^^^ None
		global | some(resolvedReference(index, currentBuild, '/'))
	}
	def resolvedReference(index: KeyIndex, currentBuild: URI, trailing: Parser[_]): Parser[ResolvedReference] =
	{
		def projectID(uri: URI) = token( examples(ID, index projects uri, "project ID") <~ trailing )
		def projectRef(uri: URI) = projectID(uri) map { id => ProjectRef(uri, id) }

		val uris = index.buildURIs
		val resolvedURI = Uri(uris).map(uri => Scope.resolveBuild(currentBuild, uri))
		val buildRef = token( '{' ~> resolvedURI <~ '}' ).?

		buildRef flatMap {
			case None => projectRef(currentBuild)
			case Some(uri) => projectRef(uri) | token(trailing ^^^ BuildRef(uri))
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
	def showParser = token( (ShowCommand ~ Space) ^^^ true) ?? false
	def scopedKeyParser(state: State): Parser[ScopedKey[_]] = scopedKeyParser(Project extract state)
	def scopedKeyParser(extracted: Extracted): Parser[ScopedKey[_]] = scopedKeyParser(extracted.structure, extracted.currentRef)
	def scopedKeyParser(structure: BuildStructure, currentRef: ProjectRef): Parser[ScopedKey[_]] =
	{
		def confs(uri: URI) = if(structure.units.contains(uri)) defaultConfigs(structure.data)(ProjectRef(uri, structure.rootProject(uri))) else Nil
		val defaultConfs: Option[ResolvedReference] => Seq[String] = {
			case None => confs(structure.root)
			case Some(BuildRef(uri)) => confs(uri)
			case Some(ref: ProjectRef) => if(Project.getProject(ref, structure).isDefined) defaultConfigs(structure.data)(ref) else Nil
		}
		scopedKey(structure.index.keyIndex, currentRef, defaultConfs, structure.index.keyMap, structure.data)
	}

	def requireSession[T](s: State, p: => Parser[T]): Parser[T] =
		if(s get sessionSettings isEmpty) failure("No project loaded") else p

	sealed trait ParsedAxis[+T]
	final object ParsedGlobal extends ParsedAxis[Nothing]
	final object Omitted extends ParsedAxis[Nothing]
	final class ParsedValue[T](val value: T) extends ParsedAxis[T]
	def value[T](t: Parser[T]): Parser[ParsedAxis[T]] = t map { v => new ParsedValue(v) }
}