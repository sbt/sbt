package xsbt.boot

// The exception to use when an error occurs at the launcher level (and not a nested exception).
// This indicates overrides toString because the exception class name is not needed to understand
// the error message.
class BootException(override val toString: String) extends RuntimeException(toString)
