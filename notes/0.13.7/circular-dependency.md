  [@eed3si9n]: https://github.com/eed3si9n

### Circular dependency

By default circular dependencies are warned, but they do not halt the dependency resolution. Using the following setting, circular dependencies can be treated as an error.

    updateOptions := updateOptions.value.withCircularDependencyLevel(CircularDependencyLevel.Error)
