/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt

	import Project.ScopedKey
	import CommandSupport.logger
	import Load.BuildStructure
	import complete.{DefaultParsers, Parser}
	import DefaultParsers._
	import java.net.URI

object Act
{
	// this does not take delegation into account
	def scopedKey(index: KeyIndex, currentBuild: URI, currentProject: String, keyMap: Map[String, AttributeKey[_]]): Parser[ScopedKey[_]] =
	{
		for {
			proj <- optProjectRef(index, currentBuild, currentProject)
			conf <- configs( index configs proj )
			key <- keyRef( index.keys(proj, conf), keyMap ) }
		yield
			ScopedKey( Scope( Select(proj), toAxis(conf map ConfigKey.apply, Global), Global, Global), key )
	}

	def toAxis[T](opt: Option[T], ifNone: ScopeAxis[Nothing]): ScopeAxis[T] =
		opt match { case Some(t) => Select(t); case None => ifNone }

	def configs(confs: Set[String]) = token( (ID examples confs) <~ ':' ).?
	def keyRef(keys: Set[String], keyMap: Map[String, AttributeKey[_]]) = token( ID examples keys flatMap getKey(keyMap) )
	def getKey(keyMap: Map[String, AttributeKey[_]])(keyString: String): Parser[AttributeKey[_]] =
		keyMap.get(keyString) match { case Some(k) => success(k); case None => failure("Invalid key: " + keyString)}

	def projectRef(index: KeyIndex, currentBuild: URI): Parser[ProjectRef] =
	{
		val uris = index.buildURIs
		val build = token( '(' ~> Uri(uris).map(uri => Scope.resolveBuild(currentBuild, uri)) <~ ')') ?? currentBuild
		def projectID(uri: URI) = token( ID.examples(index projects uri) <~ '/' )

		for(uri <- build; id <- projectID(uri)) yield
			ProjectRef(Some(uri), Some(id))
	}
	def optProjectRef(index: KeyIndex, currentBuild: URI, currentProject: String) =
		projectRef(index, currentBuild) ?? ProjectRef(Some(currentBuild), Some(currentProject))

	def valueParser(s: State, structure: BuildStructure)(key: ScopedKey[_]): Parser[() => State] =
		structure.data.get(key.scope, key.key) match
		{
			case None => failure("Invalid setting or task")
			case Some(input: InputTask[_]) => applyTask(s, structure, input.parser)
			case Some(task: Task[_]) => applyTask(s, structure, success(task))
			case Some(v) => success(() => { logger(s).info(v.toString); s})
		}
	def applyTask(s: State, structure: BuildStructure, p: Parser[Task[_]]): Parser[() => State] =
		Command.applyEffect(p) { t =>
			import EvaluateTask._
			processResult(runTask(t)(nodeView(structure, s)), logger(s))
			s
		}
	def actParser(s: State): Parser[() => State] =
		if(s get Project.SessionKey isEmpty) failure("No project loaded") else actParser0(s)
	private[this] def actParser0(state: State) =
	{
		val extracted = Project extract state
		import extracted._
		scopedKey(structure.index.keyIndex, curi, cid, structure.index.keyMap) flatMap valueParser(state, structure)
	}
}