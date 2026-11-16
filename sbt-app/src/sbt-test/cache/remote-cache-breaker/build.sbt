import sbt.util.CircuitBreakerCacheStore

lazy val checkWrapped = taskKey[Unit]("asserts the remote store is behind a circuit breaker")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

// Building the stores opens no connection, so an unreachable endpoint is enough here.
Global / remoteCache := Some(new java.net.URI("grpc://127.0.0.1:1"))

checkWrapped := Def.uncached {
  val stores = cacheStores.value
  assert(stores.exists(_.isInstanceOf[CircuitBreakerCacheStore]), stores.mkString(", "))
}
