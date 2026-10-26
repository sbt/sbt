AGENTS instructions
===================

The main developer documentation is [Contributor's guide](contributing-docs/README.md).

- [Development environment](contributing-docs/02_development_environment.md)

Compiling with sbt
------------------

```bash
sbt --client --color=false --supershell=false --batch compile
```

Pull request guideline
----------------------

- Follow the PR guidance in [CONTRIBUTING.md](./CONTRIBUTING.md).
- Keep the changed lines to the fewest that's reasonable: aim for under 50 lines, and no more than 300 lines including tests.
- [ ] Before working on a pull request, please confirm that **you can reproduce the reported problem** using GitHub Actions or your computer.
- [ ] After making the code change, please confirm that **your change compiles, and has fixed the problem**.
- [ ] Follow the [Commit message guideline](./CONTRIBUTING.md#commit), including the "Generated-by" tag for Gen-AI tools.

Coding style
------------

```bash
sbt --client --color=false --supershell=false --batch scalafmtAll
```

- Follow [Coding style and best practices](contributing-docs/03_coding_style.md)
- For newly written code, avoid braces in Scala 3.x, that is use SIP-44 Fewer Braces syntax.
- Prefer scala.util.Using over try-finally.
- Avoid inline comments!
- Keep Scaladoc to 3 lines or fewer, and avoid transcribing into English what can easily be deduced by reading the code body.
- Avoid adding helper functions to Defaults.scala; put them in main/src/main/scala/sbt/internal/Compiler.scala instead.

Tests
-----

Always add tests. For changes with small scopes prefer HedgeHog for Scala.
For changes that require coordination with file changes and tasks, use scripted test.
Write checks as plain `assert(...)`, not the ScalaTest Matchers DSL (`should`, `shouldBe`, `all(...)`).

- [contributing-docs/04_unit_tests.md](contributing-docs/04_unit_tests.md)
- [contributing-docs/05_scripted_tests.md](contributing-docs/05_scripted_tests.md)
- [contributing-docs/06_manual_tests.md](contributing-docs/06_manual_tests.md)

For example, here's how to run "actions/compile" scripted test:

```bash
sbt --color=false --supershell=false --client --batch scripted actions/compile
```

Stale snapshot in the boot directory:
after `publishLocalBin`, any non-scripted run against the `-SNAPSHOT` version (manual tests, community build, server tests)
silently loads old core JARs from `~/.sbt/boot/scala-3.*/org.scala-sbt/sbt/*-SNAPSHOT`.
Before trusting the result, delete that `-SNAPSHOT` directory (not all of `~/.sbt/boot`) and the test project's `target/`.
See [Clearing out boot and local cache](contributing-docs/06_manual_tests.md#clearing-out-boot-and-local-cache).

Tech stack
----------

- [contributing-docs/07_tech_stack.md](contributing-docs/07_tech_stack.md)

Binary compatibility
--------------------

sbt MUST maintain backward binary compatibility across minor releases.
This means removing public method signature MUST be avoided.

Use mima to check:

```bash
sbt --client --color=false --supershell=false --batch mimaReportBinaryIssues
```

Breaking compatibility with an RC or milestone release (e.g. 2.1.0-RC1) is fine; only final releases count.

Design decisions
----------------

- Restore disk cache (CAS) outputs as symlinks or copies, never hard links, which risk rewriting the cached blob.
- Don't walk output directories on every compile or cache hit; fix restored outputs at restore time.
- Keep sbtn dynamically linked against glibc; GraalVM musl support is x86_64-only and breaks ARM Linux.
- Local server/client IPC is moving to JDK AF_UNIX sockets on all platforms, including Windows; don't add named pipe shims.
- The disk cache honors `sbt.global.base/cache` on purpose, while the launcher's rt-ext cache does not.

Copyright
---------

- NEVER reproduce copyrighted material.
