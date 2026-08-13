/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.concurrent.{ ConcurrentLinkedQueue, CountDownLatch }

object WeakInternerSpec extends verify.BasicTestSuite:

  test("intern returns one instance per distinct value"):
    val pool = new WeakInterner[Key]
    val first = Key("shared")
    assert(pool.intern(first) eq first, "the first instance interned is the canonical one")
    val second = Key("shared")
    assert(first ne second)
    assert(pool.intern(second) eq first, "a value-equal input must return the canonical instance")

  test("interning preserves value equality"):
    val pool = new WeakInterner[Key]
    val k = Key("preserved")
    assert(pool.intern(k) == k)

  test("distinct values get distinct instances"):
    val pool = new WeakInterner[Key]
    val a = pool.intern(Key("a"))
    val b = pool.intern(Key("b"))
    assert(a ne b)
    assert(a == Key("a") && b == Key("b"))

  test("an interned value is released once nothing else references it"):
    // Only a weak reference to the canonical instance is kept, so a strong pool would pin it for the
    // classloader's life. The pool must let the GC reclaim it.
    val pool = new WeakInterner[Key]
    val weak = new WeakReference(pool.intern(Key("released")))
    assert(awaitCleared(weak), "the pool must not pin a value no caller references")

  test("a collected entry does not block re-interning the same value"):
    // Exercises the retry path: putIfAbsent matches a dead entry, which has to be dropped and the
    // fresh candidate published in its place.
    val pool = new WeakInterner[Key]
    val weak = new WeakReference(pool.intern(Key("recycled")))
    assert(awaitCleared(weak))
    val fresh = Key("recycled")
    assert(pool.intern(fresh) eq fresh, "a dead entry must be replaced, not returned")

  test("internWith derives the pooled instance only when the value is not pooled yet"):
    val pool = new WeakInterner[Key]
    var derived = 0
    val canonicalize: Key => Key = k =>
      derived += 1
      k
    val first = pool.internWith(Key("derived"))(canonicalize)
    assert(derived == 1, "the first intern has to derive what it pools")
    val second = pool.internWith(Key("derived"))(canonicalize)
    assert(second eq first)
    assert(derived == 1, "a hit must not derive a value it would only discard")

  test("internWith pools what canonicalize returns, not what it was given"):
    // Deriving is only worth having because the pooled instance differs from the argument -- for a
    // module report, by holding interned children. Probing by value still has to find it.
    val pool = new WeakInterner[Key]
    val canonical = Key("canonical")
    val pooled = pool.internWith(Key("canonical"))(_ => canonical)
    assert(pooled eq canonical)
    val later = pool.intern(Key("canonical"))
    assert(later eq canonical, "what canonicalize returned is the canonical instance from then on")

  test("an entry survives the collection of a value that probed for it"):
    // A miss probes with one instance and pools another, value-equal one, so the two hash to the same
    // bucket. Once the probe is collected, expunging it must not take the live entry with it.
    val pool = new WeakInterner[Key]
    val pooled = pool.internWith(Key("probed"))(_ => Key("probed"))
    // The probe is already unreachable; this waits for a GC to have run, which is what would enqueue
    // it. The canary is built outside the assert because the assert macro records -- and so retains --
    // every intermediate value it evaluates.
    val canary = new WeakReference(Key("collectable"))
    assert(awaitCleared(canary))
    val again = pool.intern(Key("probed"))
    assert(again eq pooled, "expunge must not evict a live entry a dead probe hashes to")

  test("concurrent interning of one value converges on a single instance"):
    // The parallel per-project update tasks hit these pools at once, so publication has to be atomic.
    val pool = new WeakInterner[Key]
    val results = new ConcurrentLinkedQueue[Key]
    val start = new CountDownLatch(1)
    val workers = (1 to 8).map: _ =>
      val t = new Thread(() =>
        start.await()
        var i = 0
        while i < 200 do
          results.add(pool.intern(Key("contended")))
          i += 1
      )
      t.start()
      t
    start.countDown()
    workers.foreach(_.join())
    val distinct = new IdentityHashMap[Key, Boolean]
    results.forEach(k => distinct.put(k, true))
    assert(distinct.size == 1, s"expected one canonical instance, got ${distinct.size}")

  private case class Key(name: String)

  // Weak references are cleared by the GC, which is only advisory, so retry a bounded number of times
  // rather than relying on a single System.gc().
  private def awaitCleared(ref: WeakReference[?]): Boolean =
    var i = 0
    while i < 50 && ref.get != null do
      System.gc()
      Thread.sleep(20)
      i += 1
    ref.get == null
