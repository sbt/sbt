## Internal hash function: FarmHash → MurmurHash3

The internal `farmHash` used for cache-key derivation
(`HashUtil.farmHash`, `BootServerSocket.socketLocation`) is now
implemented as two `scala.util.hashing.MurmurHash3` calls composed
into a 64-bit `Long`. Previously it called
`net.openhft.hashing.LongHashFunction.farmNa()`, whose impl uses
`sun.misc.Unsafe` and triggers the terminally-deprecated warning
under JDK 23+.

The `"farm64-"` cache-string prefix is retained for source
compatibility. Existing on-disk caches keyed on `farm64-<hex>` will
see a one-time cold rebuild after upgrade — no data loss; first
build after upgrade recomputes the hash on touched inputs.

Fixes [#8073][i8073].

  [i8073]: https://github.com/sbt/sbt/issues/8073
