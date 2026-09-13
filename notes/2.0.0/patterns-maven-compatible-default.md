## `Patterns.isMavenCompatible` now defaults to `false`

The default value of `Patterns.isMavenCompatible` changed from `true` to `false` so that the
`[organisation]`/`[organization]` token in an Ivy pattern is substituted literally, matching the
Apache Ivy specification (see [#535](https://github.com/sbt/sbt/issues/535)).

### What changed

```scala
// before: organization "org.example" rendered as "org/example"
// now:     organization "org.example" rendered as "org.example"
val p = Patterns()
  .withIvyPatterns(Vector("[organisation]/[module]/ivys/ivy-[revision].xml"))
  .withArtifactPatterns(Vector("[organisation]/[module]/[type]s/[artifact]-[revision].[ext]"))
```

Use the `[orgPath]` token if you want the slash-separated form regardless of the flag, or set
`isMavenCompatible = true` explicitly to opt back into the previous Maven m2-compatible behavior:

```scala
Patterns(/* ... */).withIsMavenCompatible(true)
```

### Who is affected

- Custom Ivy/SFTP/SSH/URL/file resolvers built with a hand-written `Patterns` that uses the
  `[organisation]` token and previously relied on the implicit dot-to-slash rewrite.
  - Under the Ivy engine, the organization is now rendered literally for these patterns.
  - Under Coursier (the default since sbt 1.3), SFTP/SSH resolvers are ignored entirely, so they
    are unaffected. Custom **URL/file** pattern resolvers, however, can still be affected: Coursier
    chooses between Maven-base extraction and Ivy-pattern parsing based on `isMavenCompatible`, so a
    resolver that relied on the old `true` default may now be parsed as an Ivy-pattern repository.
    Set `isMavenCompatible = true` explicitly to keep the Maven handling.
- The built-in `Resolver.mavenStylePatterns` is unchanged: it remains explicitly
  `isMavenCompatible = true`, so `Resolver.url`/`Resolver.file`/`Resolver.sftp`/`Resolver.ssh`
  constructed with the default patterns continue to use the Maven layout.
