sbt coding style and best practices
===================================

Some of our coding style rules are enforced programmatically by Scalafmt, which are run automatically with static checks and on every Pull Request (PR), but there are some rules that are not yet automated and are more sbt specific.

General style
-------------

### Naming

- Use short names for small scopes, for example `xs` and `x`.
- Use longer names for larger scopes.

### Functional programming

- Prefer succinct and pure functions.
- Prefer the use of `Option` and `Either`, rather than `null` and `Exception`.

### Import

- Put all imports at the top of the file

### Braces

- Prefer to omit braces in Scala 3.x, that is use SIP-44 Fewer Braces syntax, especially for fresh code.

```scala
// BAD
xs.map { x =>
  val y = x - 1
  y * y
}

// GOOD
xs.map: x =>
  val y = x - 1
  y * y
```

- Use `end` marker for a class, trait, and object definition, regardless of the length of the template.

```scala
// BAD
object O1 {
  def foo: Int = 1
}

// GOOD
object O1:
  def foo: Int = 1
end O1
```

### Infix notation

- Avoid the infix notation for non-symbolic methods `a foo 1`, and use the method call notation `a.foo(1)` instead.

### Comments

- Use ScalaDoc to provide API documentation. In general, however, document the intent and background behind the code, rather than transcribing code to English.
- Avoid exessive inline comments, especially the ones that repeats the same information as the code.

### Returns

- Avoid `return` statements.

Modular design
--------------

Because sbt has 100+ plugins, we have to be careful not to break them when we make changes.

- Maintain backward binary compatibility. That is if a plugin was published against sbt 2.0, it must work on sbt 2.1.
- Expose as few public methods as possible.
- Hide implementation details in `sbt.internal.x` packages.
- Avoid exposing types from external libraries. We often change the library uses over the course of years.

sbt build.sbt DSL
-----------------

- Set the default value in the widest scope, such as the global scope.

Hermeticity / stability
-----------------------

- Avoid capturing machine-specific details in the task, including the absolute path.
- Avoid capturing time details in the task, including timestamps.
