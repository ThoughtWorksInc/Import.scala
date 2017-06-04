package com.thoughtworks.`import`

import org.scalatest.{FreeSpec, Matchers}

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
class ImportUrlSpec extends FreeSpec with Matchers {

  "When importing a Scala file from an URL" - {
    import $url.`https://gist.github.com/Atry/5dcb1414b804fd7679393cacac3c89fc/raw/5b1748ab6b45c00be0109686fdb25e85cde11ce0/include-example.sc`.i
    "Then the members in the file should be access from current scope" in {
      i should be(42)
    }
  }

}
