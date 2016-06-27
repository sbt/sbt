package sbt.internal.util

import sbt.datatype.{ ArrayFormat, BooleanFormat, ByteFormat, IntFormat, LongFormat, StringFormat }
import sjsonnew.{ CollectionFormats, TupleFormats }

object CacheImplicits extends BasicCacheImplicits
  with ArrayFormat
  with BooleanFormat
  with ByteFormat
  with FileFormat
  with IntFormat
  with LongFormat
  with StringFormat
  with URIFormat
  with URLFormat
  with TupleFormats
  with CollectionFormats