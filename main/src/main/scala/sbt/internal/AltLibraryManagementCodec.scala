package sbt.internal

import sbt.internal.librarymanagement._
import sbt.internal.util.Types._
import sbt.internal.util.{ HList, HNil }
import sbt.io.{ Hash, IO }
import sbt.librarymanagement._
import sbt.util.CacheImplicits._
import sbt.util._
import sjsonnew.JsonFormat

object AltLibraryManagementCodec extends LibraryManagementCodec {
  type In0 = ModuleSettings :+: UpdateConfiguration :+: HNil
  type In = IvyConfiguration :+: In0

  object NullLogger extends sbt.internal.util.BasicLogger {
    override def control(event: sbt.util.ControlEvent.Value, message: ⇒ String): Unit = ()
    override def log(level: Level.Value, message: ⇒ String): Unit = ()
    override def logAll(events: Seq[sbt.util.LogEvent]): Unit = ()
    override def success(message: ⇒ String): Unit = ()
    override def trace(t: ⇒ Throwable): Unit = ()
  }

  implicit val altRawRepositoryJsonFormat: JsonFormat[RawRepository] =
    projectFormat(_.name, FakeRawRepository.create)

  // Redefine to add RawRepository, and switch to unionFormat
  override lazy implicit val ResolverFormat: JsonFormat[Resolver] =
    unionFormat8[Resolver,
                 ChainedResolver,
                 MavenRepo,
                 MavenCache,
                 FileRepository,
                 URLRepository,
                 SshRepository,
                 SftpRepository,
                 RawRepository]

  type InlineIvyHL = (IvyPaths :+: Vector[Resolver] :+: Vector[Resolver] :+: Vector[
    ModuleConfiguration] :+: Vector[String] :+: Boolean :+: HNil)
  def inlineIvyToHL(i: InlineIvyConfiguration): InlineIvyHL = (
    i.paths :+: i.resolvers :+: i.otherResolvers :+: i.moduleConfigurations :+:
      i.checksums :+: i.managedChecksums :+: HNil
  )

  type ExternalIvyHL = PlainFileInfo :+: Array[Byte] :+: HNil
  def externalIvyToHL(e: ExternalIvyConfiguration): ExternalIvyHL =
    FileInfo.exists(e.baseDirectory) :+: Hash.contentsIfLocal(e.uri) :+: HNil

  // Redefine to use a subset of properties, that are serialisable
  override lazy implicit val InlineIvyConfigurationFormat: JsonFormat[InlineIvyConfiguration] = {
    def hlToInlineIvy(i: InlineIvyHL): InlineIvyConfiguration = {
      val (paths :+: resolvers :+: otherResolvers :+: moduleConfigurations :+: localOnly
        :+: checksums :+: HNil) = i
      InlineIvyConfiguration(None,
                             IO.createTemporaryDirectory,
                             NullLogger,
                             UpdateOptions(),
                             paths,
                             resolvers,
                             otherResolvers,
                             moduleConfigurations,
                             localOnly,
                             checksums,
                             None)
    }

    projectFormat[InlineIvyConfiguration, InlineIvyHL](inlineIvyToHL, hlToInlineIvy)
  }

  // Redefine to use a subset of properties, that are serialisable
  override lazy implicit val ExternalIvyConfigurationFormat
    : JsonFormat[ExternalIvyConfiguration] = {
    def hlToExternalIvy(e: ExternalIvyHL): ExternalIvyConfiguration = {
      val baseDirectory :+: _ /* uri */ :+: HNil = e
      ExternalIvyConfiguration(
        None,
        baseDirectory.file,
        NullLogger,
        UpdateOptions(),
        IO.createTemporaryDirectory.toURI /* the original uri is destroyed.. */,
        Vector.empty)
    }

    projectFormat[ExternalIvyConfiguration, ExternalIvyHL](externalIvyToHL, hlToExternalIvy)
  }

  // Redefine to switch to unionFormat
  override implicit lazy val IvyConfigurationFormat: JsonFormat[IvyConfiguration] =
    unionFormat2[IvyConfiguration, InlineIvyConfiguration, ExternalIvyConfiguration]

  def forHNil[A <: HNil]: Equiv[A] = (_: A, _: A) => true
  implicit val lnilEquiv1: Equiv[HNil] = forHNil[HNil]
  implicit val lnilEquiv2: Equiv[HNil.type] = forHNil[HNil.type]

  implicit def hconsEquiv[H, T <: HList](implicit he: Equiv[H], te: Equiv[T]): Equiv[H :+: T] =
    (x: H :+: T, y: H :+: T) => he.equiv(x.head, y.head) && te.equiv(x.tail, y.tail)

  implicit object altIvyConfigurationEquiv extends Equiv[IvyConfiguration] {
    def equiv(x: IvyConfiguration, y: IvyConfiguration): Boolean = (x, y) match {
      case (x: InlineIvyConfiguration, y: InlineIvyConfiguration) =>
        implicitly[Equiv[InlineIvyHL]].equiv(inlineIvyToHL(x), inlineIvyToHL(y))
      case (x: ExternalIvyConfiguration, y: ExternalIvyConfiguration) =>
        implicitly[Equiv[ExternalIvyHL]].equiv(externalIvyToHL(x), externalIvyToHL(y))
      case (x: Any, y: Any) => sys error s"Trying to compare ${x.getClass} with ${y.getClass}"
    }
  }

  implicit object altInSingletonCache extends SingletonCache[In] {
    def write(to: Output, value: In) = to.write(value)
    def read(from: Input) = from.read[In]()
    def equiv = hconsEquiv(altIvyConfigurationEquiv, implicitly[Equiv[In0]])
  }
}
