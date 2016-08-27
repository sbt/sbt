
### Fixes with compatibility implications

- Deprecate old operators.

### Deprecate old operators

The no-longer-documented operators `<<=`, `<+=`, and `<++=` are deprecated,
and will be removed in sbt 1.0.

For `<<=`, the suggested migration would be to use either `:=` or `~=` operators.
The RHS of `<<=` takes an `Initialize[_]` expression, which can be converted to `:=` style
by wrapping the expression in parenthesis, and calling `.value` at the end.
For example:

    key := (key.dependsOn(compile in Test)).value

For `<+=` and `<++=`, use `+= { x.value }` and `++= { x.value }`.

  [@eed3si9n]: https://github.com/eed3si9n

