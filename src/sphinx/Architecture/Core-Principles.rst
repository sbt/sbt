=================
 Core Principles
=================

This document details the core principles overarching sbt's design and code style.   Sbt's core principles can
be stated quite simply:

1. Everything should have a ``Type``, enforced as much as is practical.
2. Dependencies should be **explicit**.
3. Once learned, a concept should hold throughout **all** parts of sbt.
4. Parallel is the default.

With these principles in mind, let's walk through the core design of sbt.


Introduction to build state
===========================
This is the first piece you hit when starting sbt.  Sbt's command engine is the means by which
it processes user requests using the build state.  The command engine is essentially a means of applying
**state transformations** on the build state, to execute user requests.

In sbt, commands are functions that take the current build state (``sbt.State``) and produce the next state.  In
other words, they are essentially functions of ``sbt.State => sbt.State``.   However, in reality, Commands are
actually string processors which take some string input and act on it, returning the next build state.

The details of the command engine are covered in :doc:`the command engine section <Command-Engine>`.

So, the entirety of sbt is driven off the ``sbt.State`` class.   Since this class needs to be resilient in the
face of custom code and plugins, it needs a mechanism to store the state from any potential client.   In
dynamic languages, this can be done directly on objects.   

A naive approach in Scala is to use a ``Map<String,Any>``.  However, this vioaltes tennant #1: Everythign should have a `Type`.
So, sbt defines a new type of map called an ``AttributeMap``.   An ``AttributeMap`` is a key-value storage mechanism where
keys are both strings *and* expected `Type`s for their value.  

Here is what the typesafe ``AttributeKey`` key looks like ::

  sealed trait AttributeKey[T] {
    /** The label is the identifier for the key and is camelCase by convention. */
	def label: String
	/** The runtime evidence for `T` */
	def manifest: Manifest[T]
  }

These keys store both a `label` (``string``) and some runtime type information (``manifest``).  To put or get something on
the AttributeMap, we first need to construct one of these keys.  Let's look at the basic definition of the ``AttributeMap`` ::

  trait AttributeMap {
	/** Gets the value of type `T` associated with the key `k` or `None` if no value is associated. 
	* If a key with the same label but a different type is defined, this method will return `None`. */
	def get[T](k: AttributeKey[T]): Option[T]

	
	/** Adds the mapping `k -> value` to this map, replacing any existing mapping for `k`.
	* Any mappings for keys with the same label but different types are unaffected. */
	def put[T](k: AttributeKey[T], value: T): AttributeMap
  }


Now that there's a definition of what build state is, there needs to be a way to dynamically construct it.  In sbt, this is
done through the ``Setting[_]`` sequence.

Introduction to Settings
========================

TODO - Discuss ``Setting[_]``

TODO - Transition into ``Task[_]``

TODO - Transition into ``InputTask[_]``