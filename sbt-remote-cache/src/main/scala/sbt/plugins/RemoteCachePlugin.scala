package sbt
package plugins

import Keys.*
import sbt.util.DiskActionCacheStore
import sbt.internal.{ GrpcActionCacheStore, HttpActionCacheStore }

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
          val headers = remoteCacheHeaders.value.toList
          val shown = HttpActionCacheStore.redact(remote)
          val tlsFiles = List(
            remoteCacheTlsCertificate.value,
            remoteCacheTlsClientCertificate.value,
            remoteCacheTlsClientKey.value,
          ).flatten
          remote.getScheme match
            case scheme @ ("grpc" | "http") =>
              val hasCreds = headers.nonEmpty || remote.getRawUserInfo != null
              val creds = if hasCreds then ", including credentials," else ""
              val secure = if scheme == "grpc" then "grpcs" else "https"
              sLog.value.warn(
                s"remoteCache $shown uses the plaintext $scheme:// scheme; traffic$creds " +
                  s"is not encrypted and can be read or altered in transit. Use $secure:// for TLS."
              )
            case _ => ()
          val r = remote.getScheme match
            case "http" | "https" =>
              if remote.getScheme == "https" && tlsFiles.nonEmpty then
                sys.error(
                  s"remoteCache $shown: remoteCacheTls* settings are not supported for https yet"
                )
              HttpActionCacheStore(
                uri = remote,
                remoteHeaders = headers,
                disk = disk,
                requestTimeout = remoteCacheRequestTimeout.value,
              )
            case _ =>
              GrpcActionCacheStore(
                uri = remote,
                rootCerts = remoteCacheTlsCertificate.value.map(_.toPath),
                clientCertChain = remoteCacheTlsClientCertificate.value.map(_.toPath),
                clientPrivateKey = remoteCacheTlsClientKey.value.map(_.toPath),
                remoteHeaders = headers,
                disk = disk,
                requestTimeout = remoteCacheRequestTimeout.value,
              )
          orig ++ Seq(r)
        case _ => orig
      end match
    },
  )
end RemoteCachePlugin
