import sbt.util.ReadOnlyCacheStore

lazy val checkWrapped = taskKey[Unit]("asserts the remote store is read-only")
lazy val checkNotWrapped = taskKey[Unit]("asserts the remote store is writable")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

// Building the stores opens no connection, so an unreachable endpoint is enough here.
Global / remoteCache := Some(new java.net.URI("grpc://127.0.0.1:1"))

def wrapped = Def.setting(cacheStores.value.exists(_.isInstanceOf[ReadOnlyCacheStore]))

checkWrapped := Def.uncached {
  assert(wrapped.value, cacheStores.value.mkString(", "))
}

checkNotWrapped := Def.uncached {
  assert(!wrapped.value, cacheStores.value.mkString(", "))
}
