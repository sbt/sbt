/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

import java.util.Collections
import org.apache.ivy.{core,plugins}
import core.module.id.ModuleRevisionId
import core.module.descriptor.DependencyDescriptor
import core.resolve.ResolveData
import core.settings.IvySettings
import plugins.resolver.{BasicResolver, DependencyResolver, IBiblioResolver}
import plugins.resolver.{AbstractPatternsBasedResolver, AbstractSshBasedResolver, FileSystemResolver, SFTPResolver, SshResolver, URLResolver}

private object ConvertResolver
{
	/** Converts the given sbt resolver into an Ivy resolver..*/
	def apply(r: Resolver, settings: IvySettings, log: Logger) =
	{
		r match
		{
			case repo: MavenRepository =>
			{
				val pattern = Collections.singletonList(Resolver.resolvePattern(repo.root, Resolver.mavenStyleBasePattern))
				final class PluginCapableResolver extends IBiblioResolver with DescriptorRequired {
					def setPatterns() { // done this way for access to protected methods.
						setArtifactPatterns(pattern)
						setIvyPatterns(pattern)
					}
				}
				val resolver = new PluginCapableResolver
				initializeMavenStyle(resolver, repo.name, repo.root)
				resolver.setPatterns() // has to be done after initializeMavenStyle, which calls methods that overwrite the patterns
				resolver
			}
			case r: JavaNet1Repository =>
			{
				// Thanks to Matthias Pfau for posting how to use the Maven 1 repository on java.net with Ivy:
				// http://www.nabble.com/Using-gradle-Ivy-with-special-maven-repositories-td23775489.html
				val resolver = new IBiblioResolver with DescriptorRequired { override def convertM2IdForResourceSearch(mrid: ModuleRevisionId) = mrid }
				initializeMavenStyle(resolver, JavaNet1Repository.name, "http://download.java.net/maven/1/")
				resolver.setPattern("[organisation]/[ext]s/[module]-[revision](-[classifier]).[ext]")
				resolver
			}
			case repo: SshRepository =>
			{
				val resolver = new SshResolver with DescriptorRequired
				initializeSSHResolver(resolver, repo, settings)
				repo.publishPermissions.foreach(perm => resolver.setPublishPermissions(perm))
				resolver
			}
			case repo: SftpRepository =>
			{
				val resolver = new SFTPResolver
				initializeSSHResolver(resolver, repo, settings)
				resolver
			}
			case repo: FileRepository =>
			{
				val resolver = new FileSystemResolver with DescriptorRequired
				resolver.setName(repo.name)
				initializePatterns(resolver, repo.patterns, settings)
				import repo.configuration.{isLocal, isTransactional}
				resolver.setLocal(isLocal)
				isTransactional.foreach(value => resolver.setTransactional(value.toString))
				resolver
			}
			case repo: URLRepository =>
			{
				val resolver = new URLResolver with DescriptorRequired
				resolver.setName(repo.name)
				initializePatterns(resolver, repo.patterns, settings)
				resolver
			}
			case repo: ChainedResolver => IvySbt.resolverChain(repo.name, repo.resolvers, false, settings, log)
			case repo: RawRepository => repo.resolver
		}
	}
    
	private sealed trait DescriptorRequired extends BasicResolver
	{
		override def getDependency(dd: DependencyDescriptor, data: ResolveData) =
		{
			val prev = descriptorString(isAllownomd)
			setDescriptor(descriptorString(hasExplicitURL(dd)))
			try super.getDependency(dd, data) finally setDescriptor(prev)
		}
		def descriptorString(optional: Boolean) =
			if(optional) BasicResolver.DESCRIPTOR_OPTIONAL else BasicResolver.DESCRIPTOR_REQUIRED
		def hasExplicitURL(dd: DependencyDescriptor): Boolean =
			dd.getAllDependencyArtifacts.exists(_.getUrl != null)
	}
	private def initializeMavenStyle(resolver: IBiblioResolver, name: String, root: String)
	{
		resolver.setName(name)
		resolver.setM2compatible(true)
		resolver.setRoot(root)
	}
	private def initializeSSHResolver(resolver: AbstractSshBasedResolver, repo: SshBasedRepository, settings: IvySettings)
	{
		resolver.setName(repo.name)
		resolver.setPassfile(null)
		initializePatterns(resolver, repo.patterns, settings)
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
					password.foreach(setUserPassword)
				case RepositoryHelpers.KeyFileAuthentication(user, file, password) =>
					setKeyFile(file)
					password.foreach(setKeyFilePassword)
					setUser(user)
			}
	}
	private def initializePatterns(resolver: AbstractPatternsBasedResolver, patterns: Patterns, settings: IvySettings)
	{
		resolver.setM2compatible(patterns.isMavenCompatible)
		resolver.setDescriptor(if (patterns.descriptorOptional) BasicResolver.DESCRIPTOR_OPTIONAL else BasicResolver.DESCRIPTOR_REQUIRED)
		resolver.setCheckconsistency(!patterns.skipConsistencyCheck)
		patterns.ivyPatterns.foreach(p => resolver.addIvyPattern(settings substitute p))
		patterns.artifactPatterns.foreach(p => resolver.addArtifactPattern(settings substitute p))
	}
}
