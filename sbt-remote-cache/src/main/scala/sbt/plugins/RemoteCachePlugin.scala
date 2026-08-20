package sbt
package plugins

import Keys.*
import sbt.util.DiskActionCacheStore
import sbt.internal.GrpcActionCacheStore

object RemoteCachePlugin extends AutoPlugin:
  override def trigger = AllRequirements
  override def requires = JvmPlugin
  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    cacheStores := {
      val orig = cacheStores.value
      val remoteOpt = remoteCache.value
      remoteOpt match
        case Some(remote) =>
          val disk = orig.collectFirst { case r: DiskActionCacheStore =>
            r
          } match
            case Some(x) => x
            case None    => sys.error("disk store not found")
          if remote.getScheme == "grpc" then
            val headers = remoteCacheHeaders.value
            val creds = if headers.nonEmpty then ", including credential headers," else ""
            sLog.value.warn(
              s"remoteCache $remote uses the plaintext grpc:// scheme; traffic$creds " +
                "is not encrypted and can be read or altered in transit. Use grpcs:// for TLS."
            )
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
