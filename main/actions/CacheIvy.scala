/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

	import Predef.{conforms => _, _}

	import FileInfo.{exists, hash}
	import java.io.File
	import java.net.URL
	import Types.{:+:, idFun}
	import scala.xml.NodeSeq
	import sbinary.{DefaultProtocol,Format}
	import DefaultProtocol.{immutableMapFormat, optionsAreFormat}
	import RepositoryHelpers._
	import Ordering._


/** InputCaches for IvyConfiguration, ModuleSettings, and UpdateConfiguration
* The InputCaches for a basic data structure is built in two parts.
* Given the data structure:
*   Data[A,B,C, ...]
* 1) Define a conversion from Data to the HList A :+: B :+: C :+: ... :+: HNil,
*    excluding any members that should not be considered for caching
* 2) In theory, 1) would be enough and wrapHL would generate InputCache[Data] as long
*    as all of InputCache[A], InputCache[B], ... exist.  However, if any of these child
*    InputCaches are constructed using wrapHL, you get a diverging implicit error.  (I
*    believe scalac is generating this error as specified, but that the implicits would
*    be valid and not be infinite.  This might take some effort to come up with a new rule
*    that allows this)
* 3) So, we need to explicitly define the intermediate implicits.  The general approach is:
*    {{{
*      object LN {
*        ... Data => HList conversions ...
*      }
*      import LN._
*      implicit dataCache: InputCache[Data] = wrapHL
*
*      object L(N-1) ...
*    }}}
*    Each Data in LN only uses implicits from L(N-1).
*    This way, higher levels (higher N) cannot see the HList conversions of subcomponents but can
*    use the explicitly defined subcomponent implicits and there is no divergence.
* 4) Ideally, diverging implicits could be relaxed so that the ... = wrapIn lines could be removed.
*/
object CacheIvy
{
	def password(s: Option[String]) = new Array[Byte](0)
	def names(s: Iterable[Configuration]): Set[String] = s.map(_.name).toSet

	import Cache._
	implicit def wrapHL[W, H, T <: HList](implicit f: W => H :+: T, cache: InputCache[H :+: T]): InputCache[W] =
		Cache.wrapIn(f, cache)

	lazy val excludeMap: Format[Map[ModuleID, Set[String]]] = implicitly
	lazy val updateIC: InputCache[IvyConfiguration :+: ModuleSettings :+: UpdateConfiguration :+: HNil] = implicitly
/*	def deliverIC: InputCache[IvyConfiguration :+: ModuleSettings :+: DeliverConfiguration :+: HNil] = implicitly
	def publishIC: InputCache[IvyConfiguration :+: ModuleSettings :+: PublishConfiguration :+: HNil] = implicitly*/

	lazy val updateReportF: Format[UpdateReport] =
	{
		import DefaultProtocol.{BooleanFormat, FileFormat, StringFormat}
		updateReportFormat
	}
	implicit def updateReportFormat(implicit m: Format[String], cr: Format[ConfigurationReport], us: Format[UpdateStats]): Format[UpdateReport] =
	{
		import DefaultProtocol.FileFormat
		wrap[UpdateReport, (File, Seq[ConfigurationReport], UpdateStats)](rep => (rep.cachedDescriptor, rep.configurations, rep.stats), { case (cd, cs, stats) => new UpdateReport(cd, cs, stats) })
	}
	implicit def updateStatsFormat: Format[UpdateStats] =
		wrap[UpdateStats, (Long,Long,Long)]( us => (us.resolveTime, us.downloadTime, us.downloadSize), { case (rt, dt, ds) => new UpdateStats(rt, dt, ds, true) })
	implicit def confReportFormat(implicit mf: Format[ModuleID], mr: Format[ModuleReport]): Format[ConfigurationReport] =
		wrap[ConfigurationReport, (String,Seq[ModuleReport],Seq[ModuleID])]( r => (r.configuration, r.modules, r.evicted), { case (c,m,v) => new ConfigurationReport(c,m,v) })
	implicit def moduleReportFormat(implicit f: Format[Artifact], ff: Format[File], mid: Format[ModuleID]): Format[ModuleReport] =
		wrap[ModuleReport, (ModuleID, Seq[(Artifact, File)], Seq[Artifact])]( m => (m.module, m.artifacts, m.missingArtifacts), { case (m, as, ms) => new ModuleReport(m, as,ms) })
	implicit def artifactFormat(implicit sf: Format[String], of: Format[Seq[Configuration]], cf: Format[Configuration], uf: Format[Option[URL]]): Format[Artifact] =
		wrap[Artifact, (String,String,String,Option[String],Seq[Configuration],Option[URL],Map[String,String])](
			a => (a.name, a.`type`, a.extension, a.classifier, a.configurations.toSeq, a.url, a.extraAttributes),
			{ case (n,t,x,c,cs,u,e) => Artifact(n,t,x,c,cs,u,e) }
		)
	implicit def exclusionRuleFormat(implicit sf: Format[String]): Format[ExclusionRule] =
		wrap[ExclusionRule, (String, String, String, Seq[String])]( e => (e.organization, e.name, e.artifact, e.configurations), { case (o,n,a,cs) => ExclusionRule(o,n,a,cs) })

	implicit def crossVersionFormat: Format[CrossVersion] = wrap(crossToInt, crossFromInt)

	private[this] final val DisabledValue = 0
	private[this] final val BinaryValue = 1
	private[this] final val FullValue = 2

		import CrossVersion.{Binary, Disabled, Full}
	private[this] val crossFromInt = (i: Int) => i match { case BinaryValue => new Binary(idFun); case FullValue => new Full(idFun); case _ => Disabled }
	private[this] val crossToInt = (c: CrossVersion) => c match { case Disabled => 0; case b: Binary => BinaryValue; case f: Full => FullValue }

	implicit def moduleIDFormat(implicit sf: Format[String], af: Format[Artifact], bf: Format[Boolean], ef: Format[ExclusionRule]): Format[ModuleID] =
		wrap[ModuleID, ((String,String,String,Option[String]),(Boolean,Boolean,Boolean,Seq[Artifact],Seq[ExclusionRule],Map[String,String],CrossVersion))](
			m => ((m.organization,m.name,m.revision,m.configurations), (m.isChanging, m.isTransitive, m.isForce, m.explicitArtifacts, m.exclusions, m.extraAttributes, m.crossVersion)),
			{ case ((o,n,r,cs),(ch,t,f,as,excl,x,cv)) => ModuleID(o,n,r,cs,ch,t,f,as,excl,x,cv) }
		)
	implicit def moduleSetIC: InputCache[Set[ModuleID]] = basicInput(defaultEquiv, immutableSetFormat)

	implicit def configurationFormat(implicit sf: Format[String]): Format[Configuration] =
		wrap[Configuration, String](_.name, s => new Configuration(s))

	implicit def classpathFormat =
	{
		import DefaultProtocol.FileFormat
		implicitly[Format[Map[String, Seq[File]]]]
	}

	object L5 {
		implicit def inlineIvyToHL = (i: InlineIvyConfiguration) => i.paths :+: i.resolvers :+: i.otherResolvers :+: i.moduleConfigurations :+: i.localOnly :+: i.checksums :+: HNil
	}
	import L5._

	implicit def inlineIvyIC: InputCache[InlineIvyConfiguration] = wrapIn
	implicit def moduleSettingsIC: InputCache[ModuleSettings] =
		unionInputCache[ModuleSettings, PomConfiguration :+: InlineConfiguration :+: EmptyConfiguration :+: IvyFileConfiguration :+: HNil]
		
	implicit def ivyConfigurationIC: InputCache[IvyConfiguration] =
		unionInputCache[IvyConfiguration, InlineIvyConfiguration :+: ExternalIvyConfiguration :+: HNil]

	object L4 {
		implicit def moduleConfToHL = (m: ModuleConfiguration) => m.organization :+: m.name :+: m.revision :+: m.resolver :+: HNil
		implicit def emptyToHL = (e: EmptyConfiguration) => e.module :+: e.ivyScala :+: e.validate :+: HNil
		implicit def inlineToHL = (c: InlineConfiguration) => c.module :+: c.dependencies :+: c.ivyXML :+: c.configurations :+: c.defaultConfiguration.map(_.name) :+: c.ivyScala :+: c.validate :+: c.overrides :+: HNil
	}
	import L4._

	implicit def emptyIC: InputCache[EmptyConfiguration] = wrapIn
	implicit def inlineIC: InputCache[InlineConfiguration] = wrapIn
	implicit def moduleConfIC: InputCache[ModuleConfiguration] = wrapIn

	object L3 {
		implicit def mavenRToHL = (m: MavenRepository) => m.name :+: m.root :+: HNil
		implicit def fileRToHL = (r: FileRepository) => r.name :+: r.configuration :+: r.patterns :+: HNil
		implicit def urlRToHL = (u: URLRepository) => u.name :+: u.patterns :+: HNil
		implicit def sshRToHL = (s: SshRepository) => s.name :+: s.connection :+: s.patterns :+: s.publishPermissions :+: HNil
		implicit def sftpRToHL = (s: SftpRepository) => s.name :+: s.connection :+: s.patterns :+: HNil
		implicit def rawRToHL = (r: RawRepository) => r.name :+: r.resolver.getClass.getName :+: HNil
		implicit def chainRToHL = (c: ChainedResolver) => c.name :+: c.resolvers :+: HNil
		implicit def moduleToHL = (m: ModuleID) => m.organization :+: m.name :+: m.revision :+: m.configurations :+: m.isChanging :+: m.isTransitive :+: m.explicitArtifacts :+: m.exclusions :+: m.extraAttributes :+: m.crossVersion :+: HNil
	}
	import L3._

	implicit lazy val chainedIC: InputCache[ChainedResolver] = InputCache.lzy(wrapIn)
	implicit lazy val resolverIC: InputCache[Resolver] =
		unionInputCache[Resolver, ChainedResolver :+: JavaNet1Repository :+: MavenRepository :+: FileRepository :+: URLRepository :+: SshRepository :+: SftpRepository :+: RawRepository :+: HNil]
	implicit def moduleIC: InputCache[ModuleID] = wrapIn
	implicitly[InputCache[Seq[Configuration]]]

	object L2 {
		implicit def updateConfToHL = (u: UpdateConfiguration) => u.retrieve :+: u.missingOk :+: HNil
		implicit def pomConfigurationHL = (c: PomConfiguration) => hash(c.file) :+: c.ivyScala :+: c.validate :+: HNil
		implicit def ivyFileConfigurationHL = (c: IvyFileConfiguration) => hash(c.file) :+: c.ivyScala :+: c.validate :+: HNil
		implicit def sshConnectionToHL = (s: SshConnection) => s.authentication :+: s.hostname :+: s.port :+: HNil

		implicit def artifactToHL = (a: Artifact) => a.name :+: a.`type` :+: a.extension :+: a.classifier :+: names(a.configurations) :+: a.url :+: a.extraAttributes :+: HNil
		implicit def exclusionToHL = (e: ExclusionRule) => e.organization :+: e.name :+: e.artifact :+: e.configurations :+: HNil
		implicit def crossToHL = (c: CrossVersion) => crossToInt(c) :+: HNil

/*		implicit def deliverConfToHL = (p: DeliverConfiguration) => p.deliverIvyPattern :+: p.status :+: p.configurations :+: HNil
		implicit def publishConfToHL = (p: PublishConfiguration) => p.ivyFile :+: p.resolverName :+: p.artifacts :+: HNil*/
	}
	import L2._

	implicit def updateConfIC: InputCache[UpdateConfiguration] = wrapIn
	implicit def pomIC: InputCache[PomConfiguration] = wrapIn
	implicit def ivyFileIC: InputCache[IvyFileConfiguration] = wrapIn
	implicit def connectionIC: InputCache[SshConnection] = wrapIn
	implicit def artifactIC: InputCache[Artifact] = wrapIn
	implicit def exclusionIC: InputCache[ExclusionRule] = wrapIn
	implicit def crossVersionIC: InputCache[CrossVersion] = wrapIn
/*	implicit def publishConfIC: InputCache[PublishConfiguration] = wrapIn
	implicit def deliverConfIC: InputCache[DeliverConfiguration] = wrapIn*/

	object L1 {
		implicit def retrieveToHL = (r: RetrieveConfiguration) => exists(r.retrieveDirectory) :+: r.outputPattern :+: HNil
		implicit def ivyPathsToHL = (p: IvyPaths) => exists(p.baseDirectory) :+: p.ivyHome.map(exists.apply) :+: HNil
		implicit def ivyScalaHL = (i: IvyScala) => i.scalaFullVersion :+: i.scalaBinaryVersion :+: names(i.configurations) :+: i.checkExplicit :+: i.filterImplicit :+: HNil
		implicit def configurationToHL = (c: Configuration) => c.name :+: c.description :+: c.isPublic :+: names(c.extendsConfigs) :+: c.transitive :+: HNil

		implicit def passwordToHL = (s: PasswordAuthentication) => Hash(s.user) :+: password(s.password) :+: HNil
		implicit def keyFileToHL = (s: KeyFileAuthentication) => Hash(s.user) :+: hash(s.keyfile) :+: password(s.password) :+: HNil

		implicit def patternsToHL = (p: Patterns) => p.ivyPatterns :+: p.artifactPatterns :+: p.isMavenCompatible :+: HNil
		implicit def fileConfToHL = (f: FileConfiguration) => f.isLocal :+: f.isTransactional :+: HNil

		implicit def externalIvyConfigurationToHL = (e: ExternalIvyConfiguration) =>
			exists(e.baseDirectory) :+: Hash(e.url) :+: HNil
	}
	import L1._

	implicit def ivyScalaIC: InputCache[IvyScala] = wrapIn
	implicit def ivyPathsIC: InputCache[IvyPaths] = wrapIn
	implicit def retrieveIC: InputCache[RetrieveConfiguration] = wrapIn
	implicit def patternsIC: InputCache[Patterns] = wrapIn
	implicit def fileConfIC: InputCache[FileConfiguration] = wrapIn
	implicit def extIvyIC: InputCache[ExternalIvyConfiguration] = wrapIn
	implicit def confIC: InputCache[Configuration] = wrapIn

	implicit def authIC: InputCache[SshAuthentication] =
		unionInputCache[SshAuthentication, PasswordAuthentication :+: KeyFileAuthentication :+: HNil]

	implicit def javaNet1IC: InputCache[JavaNet1Repository] = singleton(JavaNet1Repository)
}
