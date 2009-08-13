/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import org.apache.ivy.{core,plugins}
import core.module.id.ModuleRevisionId
import plugins.resolver.{ChainResolver, DependencyResolver, IBiblioResolver}
import plugins.resolver.{AbstractPatternsBasedResolver, AbstractSshBasedResolver, FileSystemResolver, SFTPResolver, SshResolver, URLResolver}

private object ConvertResolver
{
	/** Converts the given sbt resolver into an Ivy resolver..*/
	def apply(r: Resolver) =
	{
		r match
		{
			case repo: MavenRepository =>
			{
				val resolver = new IBiblioResolver
				initializeMavenStyle(resolver, repo.name, repo.root)
				resolver
			}
			case JavaNet1Repository =>
			{
				// Thanks to Matthias Pfau for posting how to use the Maven 1 repository on java.net with Ivy:
				// http://www.nabble.com/Using-gradle-Ivy-with-special-maven-repositories-td23775489.html
				val resolver = new IBiblioResolver { override def convertM2IdForResourceSearch(mrid: ModuleRevisionId) = mrid }
				initializeMavenStyle(resolver, JavaNet1Repository.name, "http://download.java.net/maven/1/")
				resolver.setPattern("[organisation]/[ext]s/[module]-[revision](-[classifier]).[ext]")
				resolver
			}
			case repo: SshRepository =>
			{
				val resolver = new SshResolver
				initializeSSHResolver(resolver, repo)
				repo.publishPermissions.foreach(perm => resolver.setPublishPermissions(perm))
				resolver
			}
			case repo: SftpRepository =>
			{
				val resolver = new SFTPResolver
				initializeSSHResolver(resolver, repo)
				resolver
			}
			case repo: FileRepository =>
			{
				val resolver = new FileSystemResolver
				resolver.setName(repo.name)
				initializePatterns(resolver, repo.patterns)
				import repo.configuration.{isLocal, isTransactional}
				resolver.setLocal(isLocal)
				isTransactional.foreach(value => resolver.setTransactional(value.toString))
				resolver
			}
			case repo: URLRepository =>
			{
				val resolver = new URLResolver
				resolver.setName(repo.name)
				initializePatterns(resolver, repo.patterns)
				resolver
			}
		}
	}
	private def initializeMavenStyle(resolver: IBiblioResolver, name: String, root: String)
	{
		resolver.setName(name)
		resolver.setM2compatible(true)
		resolver.setRoot(root)
	}
	private def initializeSSHResolver(resolver: AbstractSshBasedResolver, repo: SshBasedRepository)
	{
		resolver.setName(repo.name)
		resolver.setPassfile(null)
		initializePatterns(resolver, repo.patterns)
		initializeConnection(resolver, repo.connection)
	}
	private def initializeConnection(resolver: AbstractSshBasedResolver, connection: RepositoryHelpers.SshConnection)
	{
		import resolver._
		import connection._
		hostname.foreach(setHost)
		port.foreach(setPort)
		authentication foreach
			{
				case RepositoryHelpers.PasswordAuthentication(user, password) =>
					setUser(user)
					setUserPassword(password)
				case RepositoryHelpers.KeyFileAuthentication(file, password) =>
					setKeyFile(file)
					setKeyFilePassword(password)
			}
	}
	private def initializePatterns(resolver: AbstractPatternsBasedResolver, patterns: RepositoryHelpers.Patterns)
	{
		resolver.setM2compatible(patterns.isMavenCompatible)
		patterns.ivyPatterns.foreach(resolver.addIvyPattern)
		patterns.artifactPatterns.foreach(resolver.addArtifactPattern)
	}
}
