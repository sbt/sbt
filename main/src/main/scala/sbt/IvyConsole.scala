/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt

	import java.io.File
	import Attributed.blankSeq
	import Configurations.Compile
	import Def.Setting
	import Keys._

object IvyConsole
{
	final val Name = "ivy-console"
	lazy val command = 
		Command.command(Name) { state =>
			val Dependencies(managed, repos, unmanaged) = parseDependencies(state.remainingCommands, state.log)
			val base = new File(CommandUtil.bootDirectory(state), Name)
			IO.createDirectory(base)

			val (eval, structure) = Load.defaultLoad(state, base, state.log)
			val session = Load.initialSession(structure, eval)
			val extracted = Project.extract(session, structure)
				import extracted._

			val depSettings: Seq[Setting[_]] = Seq(
				libraryDependencies ++= managed.reverse,
				resolvers ++= repos.reverse,
				unmanagedJars in Compile ++= Attributed blankSeq unmanaged.reverse,
				logLevel in Global := Level.Warn,
				showSuccess in Global := false
			)
			val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, depSettings)
                   
			val newStructure = Load.reapply(session.original ++ append, structure)
			val newState = state.copy(remainingCommands = "console-quick" :: Nil)
			Project.setProject(session, newStructure, newState)
		}

	final case class Dependencies(managed: Seq[ModuleID], resolvers: Seq[Resolver], unmanaged: Seq[File])
	def parseDependencies(args: Seq[String], log: Logger): Dependencies = (Dependencies(Nil, Nil, Nil) /: args)( parseArgument(log) )
	def parseArgument(log: Logger)(acc: Dependencies, arg: String): Dependencies =
		if(arg contains " at ")
			acc.copy(resolvers = parseResolver(arg) +: acc.resolvers)
		else if(arg endsWith ".jar")
			acc.copy(unmanaged = new File(arg) +: acc.unmanaged)
		else
			acc.copy(managed = parseManaged(arg, log) ++ acc.managed)

	private[this] def parseResolver(arg: String): MavenRepository =
	{
		val Array(name, url) = arg.split(" at ")
		new MavenRepository(name.trim, url.trim)
	}

	val DepPattern = """([^%]+)%(%?)([^%]+)%([^%]+)""".r
	def parseManaged(arg: String, log: Logger): Seq[ModuleID] =
		arg match
		{
			case DepPattern(group, cross, name, version) =>
				val crossV = if(cross.trim.isEmpty) CrossVersion.Disabled else CrossVersion.binary
				ModuleID(group.trim, name.trim, version.trim, crossVersion = crossV) :: Nil
			case _ => log.warn("Ignoring invalid argument '" + arg + "'"); Nil
		}
}
