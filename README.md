# Import.scala

**Import.scala** is a Scala compiler plugin that enables magic imports.

## Setup

```sbt
addCompilerPlugin("com.thoughtworks.import" %% "import" % "latest.release")
```

## Magic Imports

This plugin provides a set of magic imports that let you load additional
code into a Scala source file: these are imports which start with a `$`.

The syntax is similar to [magic imports in Ammonite](http://www.lihaoyi.com/Ammonite/#MagicImports).

### `import $file`
     
This lets you load code snippets into current source file. For
example given a small script defining one value we want

```scala
// MyScript.sc
val elite = 31337
```

We can load it into our current source file using:

```scala
import $file.MyScript
assert(MyScript.elite == 31337)
```


If the script is in a sub-folder, simply use:

```scala
import $file.myfolder.MyScript
```

Or if the script is in an *outer* folder,

```scala
import $file.`..`.MyScript
```

Or if you want to import the contents of the script in one go:

```scala
import $file.MyScript, MyScript._
assert(elite == 31337)
```

Or if you want to download the script from a website:

```scala
import $file.`https://gist.github.com/Atry/5dcb1414b804fd7679393cacac3c89fc/raw/5b1748ab6b45c00be0109686fdb25e85cde11ce0/include-example.sc`
assert(`https://gist.github.com/Atry/5dcb1414b804fd7679393cacac3c89fc/raw/5b1748ab6b45c00be0109686fdb25e85cde11ce0/include-example.sc`.i == 42)
```

Or if you prefer using dot as the path separator:

```scala
import $file.`https://gist.github.com`.Atry.`5dcb1414b804fd7679393cacac3c89fc`.raw.`5b1748ab6b45c00be0109686fdb25e85cde11ce0`.`include-example`
assert(`include-example`.i == 42)
```

While this is a trivial example, your `MyScript.sc` file can
contain anything you want, not just `val`s: function
`def`s, `class`es `object`s or
`trait`s, or `import`s from *other* scripts.


#### Cannot directly import from inside a Script

 You cannot import things from "inside" that script in
one chain:
```scala
import $file.MyScript._
```

Rather, you must always import the script-object first, and then import
things from the script object after:

```scala
import $file.MyScript, MyScript._
```
#### Renamed-scripts and multiple-scripts


You can re-name scripts on-import if you find their names are
colliding:

```scala
import $file.{MyScript => FooBarScript}, FooBarScript._
```

Or import multiple scripts at once

```scala
import $file.{MyScript, MyOtherScript}
```

These behave as you would expect imports to work. Note that when
importing multiple scripts, you have to name them explicitly and
cannot use wildcard `._` imports:

```scala
import $file._ // doesn't work
```
### `import $exec`
        
This is similar to `import $file`, except that it dumps the definitions and imports from the script into your current source file.
This is useful if you are using a script to hold a set of common imports.

```scala
import $exec.MyScript
assert(elite == 31337)
```

```scala
import $exec.`https://gist.github.com/Atry/5dcb1414b804fd7679393cacac3c89fc/raw/5b1748ab6b45c00be0109686fdb25e85cde11ce0/include-example.sc`
assert(i == 42)
```

## Acknowledgements

The examples in this README.md file is based on Li Haoyi's [Ammonite document](http://www.lihaoyi.com/Ammonite/#MagicImports) and copyright licensed under MIT. See the [Markdown source](https://github.com/ThoughtWorksInc/import.scala/raw/master/README.md).

<!--
License
=======


The MIT License (MIT)

Copyright (c) 2014 Li Haoyi (haoyi.sg@gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
DEALINGS IN THE SOFTWARE.
-->
