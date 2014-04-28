======
Scopes
======

Esta página describe los *scopes*. Se asume que usted ha leído y comprendido la página previa,
:doc:`.sbt build definition <Basic-Def>`.

La historia completa sobre las *keys*
-------------------------------------

:doc:`Previamente <Basic-Def>` supusimos que una *key* como
:key:`name` correspondía a una entrada en el mapa de sbt de pares llave-valor (key-value). Esto
fue una simplificación.

En verdad, cada llave puede tener un valor asociado en más de un contexto,
llamado un "scope".

Algunos ejemplos concretos:

-  Si usted tiene múltiples proyectos en la definición de la construcción, una *key* puede
   tener un valor diferente en cada proyecto.
-  La *key* :key:`compile` puede tener un valor diferente para sus archivos de código fuente
   de main comparado con el correspondiente valor para el código fuente de test, si usted
   desea que se compilen de manera distinta.
-  La *key* :key:`packageOpitons` (que contiene opciones para crear paquetes jar)
   puede tener diferentes valores para el empaquetado de archivos class (:key:`packageBin`) o para
   el empaquetado de código fuente (:key:`packageSrc`).

*No hay un único valor para una key dada*, porque el valor puede variar de acuerdo con el *scope*.

Sin embargo, existe un único valor para una *scoped key* (llaves con un contexto).

Si usted se imagina que sbt está procesando una lista de *settings* para generar
un mapa de llave-valor (*key-value*) que describe al proyecto, como :doc:`se discutió anteriormente <Basic-Def>`,
las *keys* en dicho mapa son *scoped keys*.
Cada *setting* definido en la definición de la construcción del proyecto (por ejemplo en
`build.sbt`) aplica a una *scoped key* también.

Con frecuencia el *scope* es implícito o tiene un valor por default, pero si dichos valores
son incorrectos, entonces tendrá que indicar el *scope* deseado en `build.sbt`.

Ejes del Scope
--------------

Un *eje del scope* es un tipo, donde cada instancia del tipo puede definir su propio *scope* (esto es,
cada instancia puede tener sus propios valores únicos para las *keys*).

Hay tres ejes del scope: 

-  Projects
-  Configurations
-  Tasks

Scoping mediante del eje del proyecto
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Si usted :doc:`coloca múltiples proyectos en una construcción única <Multi-Project>`, cada proyecto necesita sus propios *settings*.
Es decir, las *keys* pueden estar en *scope* de acuerdo al proyecto.

Los ejes del proyecto también pueden configurarse para la "entera construcción", de modo que un *setting* aplique
a la construcción completa más bien que a un solo proyecto. Los *settings* de *nivel de construcción*
con frecuencia se usan como un plan de reserva cuando un proyecto no define un *setting* específico para un proyecto.

Scoping mendiante el eje de configuración
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Una configuración define el tipo de construcción, potencialmente con su propio
classpath, código fuente, paquetes generados, etc. El concepto de configuración
viene de Iviy, que sbt usa para :doc:`managed dependencies <Library-Dependencies>`, y para
`MavenScopes <http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope>`_.

Algunas configuraciones que verá en sbt: 

-  `Compile` que define la construcción principal (*main*) (`src/main/scala`).
-  `Test` que define cómo construir tests (`src/test/scala`).
-  `Runtime` que define el classpath para la *task* `run`.

Por default, todas las llaves asociadas con la compilación, empaquetamiento y la ejecución
tienen un scope de configuración y por lo tanto pueden funcionar de manera diferente en cada
configuración. Los ejemplos más obvios son las *keys* para *tasks*
:key:`compile`, :key:`package`, y :key:`run`; pero todas las llaves que *afectan* dichas *keys*
(tales como :key:`sourceDirectories` o :key:`scalacOptions` o
:key:`fullClasspath`) también tienen scope de configuración.

Scoping mediante el eje task
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Los *settings* pueden afectar cómo funcionan las *tasks*. Por ejemplo, la *key* de *setting* :key:`packageOptions` afecta
a la *key* :key:`packageSrc` de *task*.

Para soportar esto, una *key de task* (tal como :key:`packageSrc`) puede ser el scopde para otra *key* (tal como :key:`packageOptions`).

Las diferentes *tasks* que construyen un paquete (:key:`packageSrc`,
:key:`packageBin`, :key:`packageDoc`) pueden compartir *keys* relacionadas al empaquetamiento,
tales como :key:`artifactName` y :key:`packageOptions`. Dichas *keys* pueden tener distintos valores para cada
*task* de empaquetamiento.

Scope global
------------

Cada eje de scope puede llenarse con una instancia del tipo de eje (por ejemplo
el eje de *task* puede llevarse con una *task*), o el eje puede llenarse con el 
valor especial `Global`.

`Global` significa lo que usted espera: el valor del *setting* aplica a todas
las instancias de ese eje. Por ejemplo, si el eje de la *task* es `Global`,
entonces dicho *setting* aplicaría a todas las *tasks*.







Delegation
----------

A scoped key may be undefined, if it has no value associated with it in
its scope.

For each scope, sbt has a fallback search path made up of other scopes.
Typically, if a key has no associated value in a more-specific scope,
sbt will try to get a value from a more general scope, such as the
`Global` scope or the entire-build scope.

This feature allows you to set a value once in a more general scope,
allowing multiple more-specific scopes to inherit the value.

You can see the fallback search path or "delegates" for a key using the
`inspect` command, as described below. Read on.

Referring to scoped keys when running sbt
-----------------------------------------

On the command line and in interactive mode, sbt displays (and parses)
scoped keys like this:

.. code-block:: text

    {<build-uri>}<project-id>/config:intask::key

-  `{<build-uri>}<project-id>` identifies the project axis. The
   `<project-id>` part will be missing if the project axis has "entire
   build" scope.
-  `config` identifies the configuration axis.
-  `intask` identifies the task axis.
-  `key` identifies the key being scoped.

`*` can appear for each axis, referring to the `Global` scope.

If you omit part of the scoped key, it will be inferred as follows:

-  the current project will be used if you omit the project.
-  a key-dependent configuration will be auto-detected if you omit the
   configuration or task.

For more details, see :doc:`/Detailed-Topics/Inspecting-Settings`.

Examples of scoped key notation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  :key:`fullClasspath` specifies just a key, so the default scopes are used:
   current project, a key-dependent configuration, and global task
   scope.
-  `test:fullClasspath` specifies the configuration, so this is
   :key:`fullClasspath` in the `test` configuration, with defaults for
   the other two scope axes.
-  `*:fullClasspath` specifies `Global` for the configuration,
   rather than the default configuration.
-  `doc::fullClasspath` specifies the :key:`fullClasspath` key scoped
   to the `doc` task, with the defaults for the project and
   configuration axes.
-  `{file:/home/hp/checkout/hello/}default-aea33a/test:fullClasspath`
   specifies a project,
   `{file:/home/hp/checkout/hello/}default-aea33a`, where the project
   is identified with the build `{file:/home/hp/checkout/hello/}` and
   then a project id inside that build `default-aea33a`. Also
   specifies configuration `test`, but leaves the default task axis.
-  `{file:/home/hp/checkout/hello/}/test:fullClasspath` sets the
   project axis to "entire build" where the build is
   `{file:/home/hp/checkout/hello/}`
-  `{.}/test:fullClasspath` sets the project axis to "entire build"
   where the build is `{.}`. `{.}` can be written `ThisBuild` in
   Scala code.
-  `{file:/home/hp/checkout/hello/}/compile:doc::fullClasspath` sets
   all three scope axes.

Inspecting scopes
-----------------

In sbt's interactive mode, you can use the `inspect` command to
understand keys and their scopes. Try `inspect test:fullClasspath`:

.. code-block:: text

    $ sbt
    > inspect test:fullClasspath
    [info] Task: scala.collection.Seq[sbt.Attributed[java.io.File]]
    [info] Description:
    [info]  The exported classpath, consisting of build products and unmanaged and managed, internal and external dependencies.
    [info] Provided by:
    [info]  {file:/home/hp/checkout/hello/}default-aea33a/test:fullClasspath
    [info] Dependencies:
    [info]  test:exportedProducts
    [info]  test:dependencyClasspath
    [info] Reverse dependencies:
    [info]  test:runMain
    [info]  test:run
    [info]  test:testLoader
    [info]  test:console
    [info] Delegates:
    [info]  test:fullClasspath
    [info]  runtime:fullClasspath
    [info]  compile:fullClasspath
    [info]  *:fullClasspath
    [info]  {.}/test:fullClasspath
    [info]  {.}/runtime:fullClasspath
    [info]  {.}/compile:fullClasspath
    [info]  {.}/*:fullClasspath
    [info]  */test:fullClasspath
    [info]  */runtime:fullClasspath
    [info]  */compile:fullClasspath
    [info]  */*:fullClasspath
    [info] Related:
    [info]  compile:fullClasspath
    [info]  compile:fullClasspath(for doc)
    [info]  test:fullClasspath(for doc)
    [info]  runtime:fullClasspath

On the first line, you can see this is a task (as opposed to a setting,
as explained in :doc:`.sbt build definition <Basic-Def>`).
The value resulting from the task will have type
`scala.collection.Seq[sbt.Attributed[java.io.File]]`.

"Provided by" points you to the scoped key that defines the value, in
this case
`{file:/home/hp/checkout/hello/}default-aea33a/test:fullClasspath`
(which is the :key:`fullClasspath` key scoped to the `test`
configuration and the `{file:/home/hp/checkout/hello/}default-aea33a`
project).

"Dependencies" may not make sense yet; stay tuned for the :doc:`next page <More-About-Settings>`.

You can also see the delegates; if the value were not defined, sbt would
search through:

-  two other configurations (`runtime:fullClasspath`,
   `compile:fullClasspath`). In these scoped keys, the project is
   unspecified meaning "current project" and the task is unspecified
   meaning `Global`
-  configuration set to `Global` (`*:fullClasspath`), since project
   is still unspecified it's "current project" and task is still
   unspecified so `Global`
-  project set to `{.}` or `ThisBuild` (meaning the entire build, no
   specific project)
-  project axis set to `Global` (`*/test:fullClasspath`) (remember,
   an unspecified project means current, so searching `Global` here is
   new; i.e. `*` and "no project shown" are different for the project
   axis; i.e. `*/test:fullClasspath` is not the same as
   `test:fullClasspath`)
-  both project and configuration set to `Global`
   (`*/*:fullClasspath`) (remember that unspecified task means
   `Global` already, so `*/*:fullClasspath` uses `Global` for all
   three axes)

Try `inspect fullClasspath` (as opposed to the above example,
`inspect test:fullClasspath`) to get a sense of the difference.
Because the configuration is omitted, it is autodetected as `compile`.
`inspect compile:fullClasspath` should therefore look the same as
`inspect fullClasspath`.

Try `inspect *:fullClasspath` for another contrast.
:key:`fullClasspath` is not defined in the `Global` configuration by
default.

Again, for more details, see :doc:`/Detailed-Topics/Inspecting-Settings`.

Referring to scopes in a build definition
-----------------------------------------

If you create a setting in `build.sbt` with a bare key, it will be
scoped to the current project, configuration `Global` and task
`Global`:

::

    name := "hello"

Run sbt and `inspect name` to see that it's provided by
`{file:/home/hp/checkout/hello/}default-aea33a/*:name`, that is, the
project is `{file:/home/hp/checkout/hello/}default-aea33a`, the
configuration is `*` (meaning global), and the task is not shown
(which also means global).

`build.sbt` always defines settings for a single project, so the
"current project" is the project you're defining in that particular
`build.sbt`. (For :doc:`multi-project builds <Multi-Project>`, each project has its own `build.sbt`.)

Keys have an overloaded method called `in` used to set the scope. The
argument to `in` can be an instance of any of the scope axes. So for
example, though there's no real reason to do this, you could set the
name scoped to the `Compile` configuration:

::

    name in Compile := "hello"

or you could set the name scoped to the :key:`packageBin` task (pointless!
just an example):

::

    name in packageBin := "hello"

or you could set the name with multiple scope axes, for example in the
:key:`packageBin` task in the `Compile` configuration:

::

    name in (Compile, packageBin) := "hello"

or you could use `Global` for all axes:

::

    name in Global := "hello"

(`name in Global` implicitly converts the scope axis `Global` to a
scope with all axes set to `Global`; the task and configuration are
already `Global` by default, so here the effect is to make the project
`Global`, that is, define `*/*:name` rather than
`{file:/home/hp/checkout/hello/}default-aea33a/*:name`)

If you aren't used to Scala, a reminder: it's important to understand
that `in` and `:=` are just methods, not magic. Scala lets you write
them in a nicer way, but you could also use the Java style:

::

    name.in(Compile).:=("hello")

There's no reason to use this ugly syntax, but it illustrates that these
are in fact methods.

When to specify a scope
-----------------------

You need to specify the scope if the key in question is normally scoped.
For example, the :key:`compile` task, by default, is scoped to `Compile`
and `Test` configurations, and does not exist outside of those scopes.

To change the value associated with the :key:`compile` key, you need to
write `compile in Compile` or `compile in Test`. Using plain
:key:`compile` would define a new compile task scoped to the current
project, rather than overriding the standard compile tasks which are
scoped to a configuration.

If you get an error like *"Reference to undefined setting"*, often
you've failed to specify a scope, or you've specified the wrong scope.
The key you're using may be defined in some other scope. sbt will try to
suggest what you meant as part of the error message; look for "Did you
mean compile:compile?"

One way to think of it is that a name is only *part* of a key. In
reality, all keys consist of both a name, and a scope (where the scope
has three axes). The entire expression
`packageOptions in (Compile, packageBin)` is a key name, in other
words. Simply :key:`packageOptions` is also a key name, but a different one
(for keys with no `in`, a scope is implicitly assumed: current
project, global config, global task).

Next
----

Now that you understand scopes, you can :doc:`learn more about settings <More-About-Settings>`.
