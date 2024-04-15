package sbt
package plugins

import Keys.*
import sbt.util.DiskActionCacheStore
import sbt.internal.GrpcActionCacheStore

object RemoteCachePlugin extends AutoPlugin:
  override def trigger = AllRequirements
  override def requires = JvmPlugin
  override def globalSettings: Seq[Def.Setting[_]] = Seq(
    cacheStores := {
      val orig = cacheStores.value
      val remoteOpt = remoteCache.value
      remoteOpt match
        case Some(remote) =>
          val disk = orig.collect { case r: DiskActionCacheStore =>
            r
          }.headOption match
            case Some(x) => x
            case None    => sys.error("disk store not found")
          val r = GrpcActionCacheStore(
            uri = remote,
            rootCerts = remoteCacheTlsCertificate.value.map(_.toPath),
            clientCertChain = remoteCacheTlsClientCertificate.value.map(_.toPath),
            clientPrivateKey = remoteCacheTlsClientKey.value.map(_.toPath),
            remoteHeaders = remoteCacheHeaders.value.toList,
            disk = disk,
          )
          orig ++ Seq(r)
        case _ => orig
    },
  )
end RemoteCachePlugin
