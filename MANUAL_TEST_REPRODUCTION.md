# Manual Test Reproduction for Issue #8665

## Problem Reproduction

This demonstrates the issue where `platform := "native0.5"` incorrectly applies the platform suffix to auto-injected Scala library dependencies.

### Steps to Reproduce (Before Fix)

1. Create a test project with the following `build.sbt`:

```scala
val scala3Version = "3.7.4"

platform := "native0.5"
scalaVersion := scala3Version
crossScalaVersions := Seq(scala3Version, "2.13.18")
organization := "org.example"

lazy val base = project
  .in(file("base"))

lazy val projectA = project
  .in(file("project-A"))
  .settings(
    libraryDependencies += (organization.value %% (base / normalizedName).value % version.value)
  )
```

2. Run:
```bash
sbt
> +base/publishLocal
> ++2.13.18 projectA/compile  # This works
> ++3.7.4 projectA/compile     # This fails with the bug
```

### Expected Behavior (After Fix)

With the fix, `++3.7.4 projectA/compile` should succeed because:
- The auto-injected `scala3-library` has `.platform(Platform.jvm)` explicitly set
- It should resolve as `scala3-library_3`, NOT `scala3-library_native0.5_3`
- The project platform should only apply to dependencies without explicit platforms

### Verification

After applying the fix, verify that:
1. The auto-injected Scala library resolves correctly without the platform suffix
2. Explicit dependencies with platforms still work correctly
3. Dependencies without explicit platforms still get the project platform

