=========================
`.sbt` Build Definition
=========================

Esta página describe las *build definitions*, incluyendo algo de "teoría" y
la sintaxis de `build.sbt`. Se asume que usted sabe como :doc:`usar sbt <Running>` y que ha leído las páginas previas en la
:doc:`Guía de inicio <index.html>`.

`.sbt` vs. `.scala` Build Definition
----------------------------------

Una *build definition* para sbt puede contener archivos con terminación `.sbt`, localizados
en el directorio base de un proyecto, y archivos con extensión `.scala`, localizados en el
subdirectorio `project/` del directorio base.

Esta página trata sobre los archivos `.sbt`, que son apropiados para la mayoría de los casos.
Los archivos `.scala` se usan típicamente para compartir código entre archivos `.sbt` y para 
*build definitions* más complicadas.

Vea :doc:`.scala build definition <Full-Def>` (más adelante en la *Guía de inicio*) para más
información sobre los archivos `.scala`.

¿Qué es una *Build Definition*?
-------------------------------

Después de examinar un proyecto y procesar los archivos para la definición de la construcción del proyecto,
sbt termina con un mapa inmutable (un conjunto de pares llave-valor) describiendo la construcción.

Por ejemplo, una llave es :key:`name` y se mapea a un valor de tipo cadena (`String`), el nombre de su proyecto.

*Los archivos de definición de la construcción no afectan el mapa de sbt directamente.*

En lugar de esto, la definición de la construcción crea una lista enorme de objectos con el tipo
`Setting[T]` donde `T` es el tipo del valor en el mapa. Un `Setting` describe una *transformación del mapa*,
tal como añadir un nuevo valor llave-valor o agregar a un valor existente. (En el espíritu de la
programación funcional con estructuras de datos y valores inmutables, una transformación regresa un
nuevo mapa - no se actualiza el viejo mapa en sí mismo).

En `build.sbt`, usted puede crear un `Setting[String]` para el nombre de su proyecto como se indica a continuación:

::

    name := "hello"

Este `Setting[String]` transforma el mapa al añadir (o reemplazar) la llave `name`, dándole el valor

`"hello"`. El mapa transformado se convierte en el nuevo mapa de sbt.

Para crear el mapa, sbt primero ordena la lista de *settings* (configuraciones) de modo que todos
los cambios al mismo se realicen juntos, y los valores que dependen de otras llaves se procesan después
de las llaves de las que dependen. Entonces sbt visita la lista ordenada de `Settings`\ s y aplica cada uno al mapa a la vez.

Resumen: Una definición de construcción define una lista de `Setting[T]`, donde un
`Setting[T]` es una transformación que afecta el mapa de pares de llaves-valores de sbt
y `T` es el tipo de cada valor.

De qué manera `build.sbt` define la configuración
-------------------------------------------------

`build.sbt` define una `Seq[Setting[_]]`; se trata de una lista de expresiones
de Scala, separada por líneas en blanco, donde cada una se convierte en un elemento
de la secuencia. Si usted colocara `Seq(` antes del contenido de un archivo `.sbt`
y `)` al final y reemplazara las líneas blancas con comas, entonces estaría observando
el código `.scala` equivalente. 

A continuación se muestra un ejemplo:

::

    name := "hello"

    version := "1.0"

    scalaVersion := "2.10.4"

Cada `Setting` se define con una expresión de Scala.
Las expresiones en `build.sbt` son independientes la una de la otra, y
son expresiones, más bien que sentencias completas de Scala. Estas expresiones
pueden estar entremezcladas con `val`\ s, `lazy val`\s, y `def`\ s.
No se permiten `object`\ s ni `class`\ es en `build.sbt`.
Estos deben ir en el directorio `project/` como archivos de código fuente completos.

Por la izquierda, :key:`name`, :key:`version`, y :key:`scalaVersion` son *keys* (llaves).
Una *key* es una instancia de `SettingKey[T]`, `TaskKey[T]`, o `InputKey[T]` donde `T` es
el valor esperado para el tipo. La clase de *keys* se explican abajo.

Las *keys* tienen un método llamado `:=`, que regresa un `Setting[T]`. Usted podría usar
una sintáxis similar a la de Java para invocar al método: 

::

    name.:=("hello")

Pero Scala permite usar `name := "hello"` en lugar de lo anterior (en Scala, un método con un único
parámetro puede utilizar cualquiera de las dos sintaxis).

El método `:=` en la *key* :key:`name` regresa un `Setting`, específicamente un
`Setting[String]`. `String` también aparece en el tipo de :key:`name` en sí misma,
el cuál es `SettingKey[String]`. En este caso, el valor `Setting[String]` regresado es una
transformación para agregar o reemplazar la *key* :key:`name` en el mapa de sbt,
dándole el valor `"hello"`.

Si usted usa el tipo de valor equivocado, la definición de la construcción no compilará:

::

     name := 42  // no compila

Las *settings* (configuraciones) deben estar separadas por líneas en blanco
---------------------------------------------------------------------------

No es posible escribir un `build.sbt` como el siguiente:

::

    // NO compila, pues no hay líneas en blanco
    name := "hello"
    version := "1.0"
    scalaVersion := "2.10.3"

sbt necesita un tipo de delimitador para indicar donde termina una expresión y comienza
la siguiente.
    
Los archivos `.sbt` contienen una lista de expresiones de Scala, no un único programa de Scala.
Estas expresiones tienen que separarse y pasarse al compilador de manera individual.

Keys
----

Tipos
~~~~~

Existen tres tipos de llaves:

-  `SettingKey[T]`: una *key* para un valor que se calcula una sola vez (el valor es
   calculado cuando se carga el proyecto, y se mantiene).
-  `TaskKey[T]`: una *key* para un valor, llamado una *task* (tarea),
   que tiene que ser recalculada cada vez, potencialmente con efectos laterales.
-  `InputKey[T]`: una *key* para una *task* que tiene argumentos para la línea de comandos como
   entrada. Vea :doc:`/Extending/Input-Tasks` para más detalles.

Built-in Keys (Llaves ya incluídas)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Las llaves ya incluídas son simplemente campos de un objeto llamado
`Keys <../../sxr/sbt/Keys.scala.html>`_. Un archivo
`build.sbt` tiene implícitamente un `import sbt.Keys._`, de modo que
`sbt.Keys.name` puede ser referido como :key:`name`.

Custom Keys (llaves personalizadas)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Las llaves personalizadas pueden definirse con sus métodos de creación respectivos: `settingKey`, `taskKey`, e `inputKey`.
Cada método espera el tipo del valor asociado con la llave así como una descripción.
El nombre de la llave se toma del `val` al que se le asignó la llave.
Por ejemplo, para definir una llave para una nueva tarea llamado `hello`, ::

    lazy val hello = taskKey[Unit]("An example task")

Aquí se usó el hecho de que un archivo `.sbt` puede contener `val`\ s y `def`\ s además de *settings* (configuraciones).
Todas estas definiciones son evaluadas antes que las configuraciones sin importar donde se definan en el archivo.
`val`\ s y `def`\ s deben estar separadas de las *settings* mediante líneas blancas.
    
.. note::

    Típicamente, se utilizan `lazy val`\ s en lugar de `val`\ s para evitar problemas de inicialización.

Task vs. Setting keys (Llaves para *Tasks* vs. Llaves para *Settings*)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Se dice que una `TaskKey[T]` define una *task*. Las *tasks* son operaciones tales como
:key:`compile` o :key:`package`. Pueden regresar `Unit` (`Unit` es el tipo de Scala análogo a `void`),
o pueden regresar un valor relacionado con la tarea, por ejemplo, :key:`package` es una `TaskKey[File]` y
su valor es el archivo jar que este crea.

Cada vez que inicia una tarea de ejecución, por ejemplo mediante teclear :key:`compile`
en el prompt interactivo de sbt, sbt volverá a ejecutar cualquier *task* envuelta exactamente una vez.

El mapa de sbt que describe el proyecto puede mantener una cadena fija para un *setting* tal como
:key:`name`, pero tiene que haber algo de código ejecutable para una tarea como :key:`compile` -- incluso si dicho
código ejecutable eventualmente regresa una cadena, tiene que ejecutarse cada vez.

*Una key dada siempre se refiere ya sea a una task o a un setting*. Es decir, "taskiness" (si debe ejecutarse
cada vez) es una propiedad de la *key*, no del valor.

Definiendo tasks y settings
---------------------------

Usando `:=`, usted puede asignar un valor a un *setting* y un cómputo a una *task*.
En el caso de un *setting*, el valor será calculado una sola vez al momento de cargar el proyecto.
Para una tarea, el cómputo se realizará cada vez que se ejecute la tarea.

Por ejemplo, para implementar la tarea `hello` de la sección anterior, ::

    hello := { println("Hello!") }

Ya vimos un ejemplo de definición de un *setting* para el nombre del proyecto, ::

    name := "hello"

Tipos para las tareas y los settings
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Desde la perspectiva del sistema de tipos, el `Setting` creado a partir de una *task key* es
ligeramente distinta de la creada a partir de una *setting key*.
`taskKey := 42` resulta en una `Setting[Task[T]]` mientras que
`settingKey := 42` resulta en una `Setting[T]`. Para la mayoría de los propósitos
no hay diferencia, la *task key* todavía crea un valor de tipo `T`
cuando la tarea se ejecuta.

La diferencia entre los tipos `T` y `Task[T]` tiene la siguiente implicación: un
*setting* no puede depender de una *task*, poque un *setting* es evaluado únicamente una vez
al momento de cargar el proyecto y no se vuelve a ejecutar.
Se escribirá más sobre este asunto pronto en :doc:`more about settings <More-About-Settings>`.

Keys en modo sbt interactivo
----------------------------

En el modo interactivo de sbt, usted puede teclear el nombre de cualquier tarea para ejecutar
dicha tarea. Es por esto que al teclear :key:`compile` se ejecuta la *task* de compilación.
La *key* :key:`compile` es una llave para una *task*.

Si usted teclea el nombre de una *key* para *setting* más bien que una para *task*, entonces
el valor de la *key* para *setting* será mostrado. Al teclear el nombre de una *task* se ejecuta
dicha *task*, pero no se despliega el valor resultante; para ver el resultado de la *task*, use
`show <nombre de la tarea>` más bien que simplemente `<nombre de la tarea`.
La convención para los nombres de las llaves es usar `estiloDeCamello` de modo que el nombre utilizado
en la línea de comandos y el identificador de Scala sean idénticos.

Para aprender más sobre cualquier *key*, teclee `inspect <nombre de la key>` en el prompt
interactivo de sbt.
Algo de la información que `inspect` despliega no tendrá sentido todavía, pero en la parte superior
le mostrará el tipo del valor para el *setting* y una breve descripción del tal.

Imports en `build.sbt`
------------------------

Puede poner sentencias import en la parte superior de `build.sbt`; no necesitan estar
separadas por líneas en blanco.

Hay algunos imports por default, como se indica a continuación:

::

    import sbt._
    import Process._
    import Keys._

(Además, si usted tiene :doc:`archivos .scala <Full-Def>`,
el contenido de cualquier objeto `Build` o `Plugin` en estos archivos será importado.
Más sobre este asunto cuando se llegue a :doc:`definiciones de construccion .scala <Full-Def>`.)

Añadiendo dependencias (librerías)
----------------------------------

Para agregar dependencias de librerías de terceros, hay dos opciones. La primera es
añadir jars en el directorio `lib/` (para *unmanaged dependencies*) y la otra es agregar
*managed dependencies*, que se verán como se muestra a continuación en `build.sbt`:

::

    libraryDependencies += "org.apache.derby" % "derby" % "10.4.1.3"

Así es como se agrega una *managed dependency* sobre la librería Apache Derby, versión 10.4.1.3.
    
La key :key:`libraryDependencies` envuelve dos complejidades: `+=` más bien que
`:=`, y el método `%`. `+=` agrega algo al valor anterior de la *key* más bien que reemplazarlo; esto
se explica en :doc:`más sobre los settings </Getting-Started/More-About-Settings>`.
El método `%` se usa para construir un ID para un módulo de Ivy a partir de cadenas, como se explica
en :doc:`library dependencies </Getting-Started/Library-Dependencies>`.

Por lo pronto, omitiremos los detalles del manejo de las dependencias (librerías) hasta más tarde en
la Guía de inicio. Hay una :doc:`página completa </Getting-Started/Library-Dependencies>` que cubre
el tema más tarde.

A continuación
--------------

Siga con :doc:`aprenda más sobre scopes </Getting-Started/Scopes>`.
