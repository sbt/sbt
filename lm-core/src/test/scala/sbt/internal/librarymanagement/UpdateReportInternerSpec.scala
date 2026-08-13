/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import java.lang.ref.WeakReference
import java.util.Calendar
import sbt.io.IO
import sbt.librarymanagement.*

object UpdateReportInternerSpec extends verify.BasicTestSuite:

  test("interning a value-equal ConfigRef returns the same instance"):
    val a = ConfigRef("compile")
    val b = ConfigRef("compile")
    val ia = UpdateReportInterner.intern(a)
    assert(ia eq UpdateReportInterner.intern(b), "value-equal ConfigRefs should share one instance")
    assert(ia == a, "interning must preserve value equality")

  test("interning a value-equal InclExclRule shares one instance"):
    val r1 = InclExclRule().withOrganization("org.bad").withName("bad-lib")
    val r2 = InclExclRule().withOrganization("org.bad").withName("bad-lib")
    assert(r1 ne r2)
    val i1 = UpdateReportInterner.intern(r1)
    assert(i1 eq UpdateReportInterner.intern(r2))
    assert(i1 == r1)

  test("interning a value-equal File shares one instance"):
    val f1 = new File("/tmp/shared/module.jar")
    val f2 = new File("/tmp/shared/module.jar")
    assert(f1 ne f2)
    assert(UpdateReportInterner.intern(f1) eq UpdateReportInterner.intern(f2))

  test("interning a value-equal Artifact shares one instance and preserves equality"):
    def artifact =
      Artifact("lib", "jar", "jar", None, Vector(ConfigRef("compile")), None, Map.empty, None)
    val a1 = artifact
    val a2 = artifact
    assert(a1 ne a2)
    val i1 = UpdateReportInterner.intern(a1)
    assert(i1 eq UpdateReportInterner.intern(a2))
    assert(i1 == a1)

  test("interning a value-equal ModuleID shares one instance and canonicalizes nested rules"):
    def excl = InclExclRule().withOrganization("org.bad").withName("bad")
    val m1 = ModuleID("org.example", "lib", "1.0.0").withExclusions(Vector(excl))
    val m2 = ModuleID("org.example", "lib", "1.0.0").withExclusions(Vector(excl))
    assert(m1 ne m2)
    val i1 = UpdateReportInterner.intern(m1)
    val i2 = UpdateReportInterner.intern(m2)
    assert(i1 eq i2, "value-equal ModuleIDs should share one instance")
    assert(i1 == m1)
    assert(i1.exclusions.head eq i2.exclusions.head, "nested exclusion rules should be shared too")

  test("interning a value-equal Caller shares one instance"):
    def caller =
      Caller(
        ModuleID("org.parent", "parent", "2.0.0"),
        Vector(ConfigRef("compile")),
        Map.empty,
        isForceDependency = false,
        isChangingDependency = false,
        isTransitiveDependency = true,
        isDirectlyForceDependency = false
      )
    val c1 = caller
    val c2 = caller
    assert(c1 ne c2)
    assert(UpdateReportInterner.intern(c1) eq UpdateReportInterner.intern(c2))

  test("interning a value-equal ModuleReport shares one instance"):
    IO.withTemporaryDirectory: baseDir =>
      val jar = new File(baseDir, "pooled.jar")
      IO.touch(jar)
      def mr = plainModuleReport("org.example", "pooled-lib", jar)
      val a = mr
      val b = mr
      assert(a ne b)
      val ia = UpdateReportInterner.intern(a)
      assert(ia eq UpdateReportInterner.intern(b), "value-equal reports should share one instance")
      assert(ia == a, "pooling must preserve value equality")

  test("a ModuleReport carrying a publicationDate is canonicalized but not pooled"):
    IO.withTemporaryDirectory: baseDir =>
      val jar = new File(baseDir, "dated.jar")
      IO.touch(jar)
      val epoch = Calendar.getInstance()
      epoch.setTimeInMillis(0L)
      def mr = plainModuleReport("org.example", "dated-lib", jar).withPublicationDate(Some(epoch))
      val a = UpdateReportInterner.intern(mr)
      val b = UpdateReportInterner.intern(mr)
      assert(a ne b, "sharing would alias a mutable java.util.Calendar")
      assert(a == b, "the two instances must still be value-equal")
      assert(a.module eq b.module, "the immutable coordinate inside is still interned")

  test("an interned value is released once nothing else references it"):
    // ModuleID has no factory cache and the name is unique, so the pool is the only thing that could
    // retain it. A strong pool would pin it for the classloader's life.
    val weak = new WeakReference(
      UpdateReportInterner.intern(ModuleID("org.example.interner", "released-coordinate", "1.0.0"))
    )
    assert(awaitCleared(weak), "the interner must release a value once no report references it")

  test("a pooled ModuleReport is released once nothing else references it"):
    IO.withTemporaryDirectory: baseDir =>
      val jar = new File(baseDir, "transient.jar")
      IO.touch(jar)
      val weak = new WeakReference(
        UpdateReportInterner.intern(
          plainModuleReport("org.example.interner", "released-report", jar)
        )
      )
      assert(awaitCleared(weak), "the pool must not pin a report no caller references")

  private def plainModuleReport(org: String, name: String, jar: File): ModuleReport =
    val artifact = Artifact(name, "jar", "jar", None, Vector.empty, None, Map.empty, None)
    ModuleReport(ModuleID(org, name, "1.0.0"), Vector((artifact, jar)), Vector.empty)
      .withConfigurations(Vector(ConfigRef("compile")))

  private def awaitCleared(ref: WeakReference[?]): Boolean =
    var i = 0
    while i < 50 && ref.get != null do
      System.gc()
      Thread.sleep(20)
      i += 1
    ref.get == null
