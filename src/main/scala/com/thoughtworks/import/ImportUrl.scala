package com.thoughtworks.`import`

import java.io._
import java.net.URL
import java.nio.file.Paths

import scala.collection.mutable
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.VirtualFile
import scala.tools.nsc.Global
import scala.tools.nsc.plugins.{Plugin, PluginComponent}

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
final class ImportUrl(override val global: Global) extends Plugin {

  override val name: String = "import-url"

  override val components: List[PluginComponent] = List(
    new PluginComponent {
      override val global: Global = ImportUrl.this.global
      import global._
      override val phaseName: String = ImportUrl.this.name
      override val runsAfter: List[String] = List("parser")

      override def newPhase(prev: scala.tools.nsc.Phase) = new StdPhase(prev) {
        private val urls = mutable.HashSet.empty[String]

        private def importUrl(urlString: String) = {
          if (urls.synchronized { urls.add(urlString) }) {
            val writer = new CharArrayWriter()
            writer.append("package $url; object `")
            writer.append(urlString)
            writer.append("`{")
            val headerSize = writer.size
            val url = new URL(urlString)
            val reader = new InputStreamReader(url.openStream(), scala.io.Codec.UTF8.charSet)
            try {
              val buffer = Array.ofDim[Char](1500)
              while ({
                reader.read(buffer) match {
                  case -1 =>
                    false
                  case size =>
                    writer.write(buffer, 0, size);
                    true
                }
              }) {}
            } finally {
              reader.close()
            }
            writer.append("\n}\n")
            val content = writer.toCharArray

            val virtualFile = new VirtualFile(urlString) {
              override val name: String = Paths.get(url.getPath).getFileName.toString
              override def file: File = new File(urlString)
            }
            val batchSourceFile = new BatchSourceFile(virtualFile, content) {
              override def positionInUltimateSource(position: Position): Position = {
                position.withShift(-headerSize)
              }
            }
            val urlUnit = new CompilationUnit(batchSourceFile)

            currentRun.compileLate(urlUnit)

          }
        }
        override def apply(unit: CompilationUnit): Unit = {
          for (Import(expr, selectors) <- unit.body) {
            expr match {
              case q"$$url" =>
                for (ImportSelector(name, _, _, _) <- selectors) {
                  importUrl(name.decodedName.toString)
                }
              case _ =>
                expr.collect {
                  case q"$$url.$name" =>
                    importUrl(name.decodedName.toString)
                }
            }
          }
        }

      }
    }
  )
  override val description: String = "Enable magic import"
}
