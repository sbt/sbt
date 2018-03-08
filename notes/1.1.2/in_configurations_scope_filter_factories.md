### Improvements

- Adds factory methods for Configuration axis scope filters

### More ways to create ScopeFilter for Configuration axis

To create configuration axis `ScopeFilter` one has to provide actual configurations
to filter by. However it's not always possible to get hold of one. For example
`Classpaths.interSort` returns configuration names.
For cases like that there are now `inConfigurationsByKeys` and `inConfigurationsByRefs` to create `ScopeFilter`'s
