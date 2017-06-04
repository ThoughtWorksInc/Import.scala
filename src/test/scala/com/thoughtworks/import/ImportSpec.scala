package com.thoughtworks.`import`

import org.scalatest.{FreeSpec, Matchers}

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
class ImportSpec extends FreeSpec with Matchers {

  "import a file in a relative path" - {
    import $file.Importee

    "Then the content of the file should be defined in an object" in {
      Importee.j should be(42)
      Importee should be(a[Singleton])
    }
  }

  "import a file with renaming" in {
    import $file.{Importee => Renamed}

    Renamed.j should be(42)
    Renamed should be(a[Singleton])
  }

  "import an URL" in {
    import $file.`https://gist.github.com/Atry/5dcb1414b804fd7679393cacac3c89fc/raw/5b1748ab6b45c00be0109686fdb25e85cde11ce0/include-example.sc`

    `https://gist.github.com/Atry/5dcb1414b804fd7679393cacac3c89fc/raw/5b1748ab6b45c00be0109686fdb25e85cde11ce0/include-example.sc`.i should
      be(42)
    `https://gist.github.com/Atry/5dcb1414b804fd7679393cacac3c89fc/raw/5b1748ab6b45c00be0109686fdb25e85cde11ce0/include-example.sc` should
      be(a[Singleton])
  }

  "import an URL with renaming" in {
    import $file.{
      `https://gist.github.com/Atry/5dcb1414b804fd7679393cacac3c89fc/raw/5b1748ab6b45c00be0109686fdb25e85cde11ce0/include-example.sc` => Renamed
    }

    Renamed.i should be(42)
    Renamed should be(a[Singleton])
  }

  "import both files at once" - {

    import $file.{
      `https://gist.github.com/Atry/5dcb1414b804fd7679393cacac3c89fc/raw/5b1748ab6b45c00be0109686fdb25e85cde11ce0/include-example.sc`,
      Importee
    }

    "Then the content of both files should be defined in an object" in {
      `https://gist.github.com/Atry/5dcb1414b804fd7679393cacac3c89fc/raw/5b1748ab6b45c00be0109686fdb25e85cde11ce0/include-example.sc`.i should
        be(42)
      Importee.j should be(42)
    }
  }

  "import a file in a subpackage" - {
    "and the file contains transitive magic imports" in {
      import $file.subpackage.Transitive
      Transitive.i should be(42)
      Transitive.Importee.j should be(42)
    }
  }

}
