package sbt

	import java.io.File
	import java.net.URI
	import Project._
	import Scope.{GlobalScope,ThisScope}
	import Load.BuildStructure
	import Types.{idFun, Id}
	import complete.DefaultParsers

private[sbt] class SetResult(val session: SessionSettings, val verboseSummary: String, val quietSummary: String)
private[sbt] object SettingCompletions
{
	def setAll(extracted: Extracted, settings: Seq[Setting[_]]): SetResult =
	{
			import extracted._
		val r = relation(extracted.structure, true)
		val allDefs = r._1s.toSeq
		val projectScope = Load.projectScope(currentRef)
		def resolve(s: Setting[_]): Seq[Setting[_]] = Load.transformSettings(projectScope, currentRef.build, rootProject, s :: Nil)
		def rescope[T](setting: Setting[T]): Seq[Setting[_]] =
		{
			val akey = setting.key.key
			val global = ScopedKey(Global, akey)
			val globalSetting = resolve( Project.setting(global, setting.init, setting.pos) )
			globalSetting ++ allDefs.flatMap { d =>
				if(d.key == akey)
					Seq( SettingKey(akey) in d.scope <<= global)
				else
					Nil
			}
		}
		val redefined = settings.flatMap(x => rescope(x))
		val session = extracted.session.appendRaw(redefined)
		setResult(session, r, redefined)
	}
	def setThis(s: State, extracted: Extracted, settings: Seq[Project.Setting[_]], arg: String): SetResult =
	{
		import extracted._
		val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, settings)
		val r = relation(extracted.structure, true)
		val newSession = session.appendSettings( append map (a => (a, arg.split('\n').toList)))
		setResult(newSession, r, append)
	}

	def setResult(session: SessionSettings, r: Relation[ScopedKey[_], ScopedKey[_]], redefined: Seq[Setting[_]])(implicit show: Show[ScopedKey[_]]): SetResult =
	{
		val redefinedKeys = redefined.map(_.key).toSet
		val affectedKeys = redefinedKeys.flatMap(r.reverse)
		def summary(verbose: Boolean): String = setSummary(redefinedKeys, affectedKeys, verbose)
		new SetResult(session, summary(true), summary(false))
	}
	def setSummary(redefined: Set[ScopedKey[_]], affected: Set[ScopedKey[_]], verbose: Boolean)(implicit display: Show[ScopedKey[_]]): String =
	{
		val QuietLimit = 3
		def strings(in: Set[ScopedKey[_]]): Seq[String] = in.toSeq.map(sk => display(sk)).sorted
		def lines(in: Seq[String]): (String, Boolean) =
			if(in.isEmpty)
				("no settings or tasks.", false)
			else if(verbose)
				(in.mkString("\n\t", "\n\t", "\n"), false)
			else
				quietList(in)
		def quietList(in: Seq[String]): (String, Boolean) =
		{
			val (first, last) = in.splitAt(QuietLimit)
			if(last.isEmpty)
				(first.mkString(", "), false)
			else
			{
				val s = first.take(QuietLimit - 1).mkString("", ", ", " and " + last.size + " others.")
				(s, true)
			}
		}
		if(redefined.isEmpty)
			"No settings or tasks were redefined."
		else
		{
			val (redef, trimR) = lines(strings(redefined))
			val (used, trimU) = lines(strings(affected))
			val details = if(trimR || trimU) "\n\tRun `last` for details." else ""
			val valuesString = if(redefined.size == 1) "value" else "values"
			"Defining %s\nThe new %s will be used by %s%s".format(redef, valuesString, used, details)
		}
	}

	/*
	// add support to Completions for displaying information before or after completions vertically
	//   or for showing a completion on a line in columns
	// possibly show after a completion is selected
	// show available keys.  can make this sorted by relevance and show top N
	// based on the key, select scopes it is defined in (Compile, `package`) or Test with all scopes being shown on second tab
	// show 
	//   := (assign value)
	//  ~= (update value)
	//  <<= (assign dependent value)
	//  if key type is appendable, include
	//   += (append value)
	// ++= (append values)
	//   <+= (append dependent value)
	// <++= (append dependent values)
	// 
	// on execution, can indicate that the new value will be used by x, y, z and 10 more (run `last` to see all)
	// 
	val settingParser = matched(parser)
	val parser = keyParser.flatMap { key =>
		scope(key).* ~ assign ~ initialization)
	val scope = token("in" ~ Space) ~ token(scopes <~ Space) ~ token(
	val assigns = Map(
		":=" -> 
	*/
}