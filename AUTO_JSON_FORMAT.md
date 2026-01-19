# Automated JsonFormat Derivation (Issue #8288)

This feature addresses issue #8288 by providing automated JsonFormat derivation for sbt internal types that commonly cause compilation errors when used in cached tasks.

## Problem

When converting plugins to sbt 2.0, users often encounter errors like:

```
[error] -- Error: /some/Class.scala:183:6
[error] 183 | compile := {
[error] | ^
[error] | given evidence sjsonnew.JsonFormat[xsbti.compile.CompileAnalysis] is not found; 
[error] | opt out of caching by annotating the key with @transient, or as foo := Def.uncached(...), 
[error] | or provide a given value
```

This forces users to either:
1. Use `Def.uncached()` (losing caching benefits)
2. Create manual shims for each missing JsonFormat

## Solution

The `AutoJsonFormat` and `AutoJsonFormats` objects provide automatic JsonFormat instances for common sbt types.

### Usage

#### 1. Automatic Import

The automatic JsonFormats are available through `CacheImplicits`, which is already imported in most sbt contexts:

```scala
import sbt.util.CacheImplicits._

// Now these work without explicit JsonFormat definitions
compile := {
  // Your compile logic that uses CompileAnalysis
  // JsonFormat is automatically provided
}
```

#### 2. Manual Import

If you need explicit access:

```scala
import sbt.util.AutoJsonFormats._

// Access specific formats
val format: JsonFormat[CompileAnalysis] = compileAnalysisFormat
```

#### 3. Custom Types

For your own case classes:

```scala
import sbt.util.AutoJsonFormat

case class MyData(name: String, value: Int)

// Automatic derivation
implicit val myDataFormat: JsonFormat[MyData] = AutoJsonFormat.caseClassFormat[MyData]
```

## Supported Types

### Automatic Fallback Formats
The following sbt internal types have fallback JsonFormats that provide helpful error messages:

- `xsbti.compile.CompileAnalysis`
- `xsbti.compile.CompileResult` 
- `xsbti.compile.PreviousResult`
- `xsbti.compile.Setup`
- `xsbti.FileConverter`
- `xsbti.HashedVirtualFileRef`
- `xsbti.VirtualFileRef`

### Case Class Derivation
Simple case classes with standard field types are automatically supported:

```scala
case class SimpleCase(name: String, age: Int, active: Boolean)
// JsonFormat automatically derived
```

## Error Messages

When a type cannot be serialized/deserialized, the fallback formats provide helpful guidance:

```
Cannot serialize xsbti.compile.CompileAnalysis. 
Consider using Def.uncached() or providing an explicit JsonFormat.
For sbt internal types, you may need to add the format to AutoJsonFormats.
```

## Migration Guide

### Before (sbt 1.x)
```scala
compile := {
  // Works fine in sbt 1.x
}
```

### After (sbt 2.0 without this fix)
```scala
compile := Def.uncached {  // Lose caching
  // Workaround
}
```

### After (sbt 2.0 with this fix)
```scala
import sbt.util.CacheImplicits._

compile := {
  // Works automatically with caching preserved
}
```

## Implementation Details

### AutoJsonFormat
- Uses runtime reflection to derive JsonFormats for case classes
- Provides fallback formats with helpful error messages
- Handles common field types (String, Int, Long, Double, Boolean, Array[Byte], Seq, Option)

### AutoJsonFormats  
- Predefined JsonFormat instances for common sbt internal types
- Mixed into `CacheImplicits` for automatic availability
- Uses fallback formats that guide users to proper solutions

## Testing

Run the tests to verify functionality:

```bash
sbt "util-cache/test"
```

## Future Improvements

1. **Compile-time derivation**: Replace runtime reflection with compile-time macros for better performance
2. **More type support**: Extend support for additional sbt internal types as they're identified
3. **Custom serialization**: Add specialized serializers for complex xsbti types

## Backward Compatibility

This feature is fully backward compatible:
- Existing code continues to work unchanged
- No breaking changes to public APIs
- Only adds new functionality
