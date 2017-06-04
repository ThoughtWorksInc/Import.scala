package com.thoughtworks.`import`

import java.io._
import java.net.URL
import java.nio.file.Paths

import scala.collection.mutable
import scala.reflect.internal.NoPhase
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.VirtualFile
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.transform.Transform

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

      override def newPhase(prev: Phase): Phase = {
        new StdPhase(prev) { currentPhase =>
          override def apply(unit: CompilationUnit): Unit = {
            transformer.transformUnit(unit)
          }

          private def transformer = new Transformer {
            override def transformStats(stats: List[Tree], exprOwner: Symbol): List[Tree] =
              stats.flatMap {
                case Import(q"$$url", selectors) =>
                  selectors.map {
                    case ImportSelector(importee, namePos, rename, renamePos) =>
                      val writer = new CharArrayWriter()
                      writer.append("object ")
                      writer.append(rename.encodedName.toString)
                      writer.append("{")
                      val headerSize = writer.size
                      val urlString = importee.decodedName.toString
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
                      def applyUntil(phase: GlobalPhase): Unit = {
                        phase.prev match {
                          case null | NoPhase =>
                          case prev: GlobalPhase =>
                            applyUntil(prev)

                        }
                        enteringPhase(phase) {
                          phase.applyPhase(urlUnit)
                        }
                      }
                      applyUntil(currentPhase)
                      val q"package $emptyPackage { $moduleDef }" = urlUnit.body
                      moduleDef
                  }
                case stat =>
                  super.transformStats(List(stat), exprOwner)
              }
          }

        }
      }

    }
  )
  override val description: String = "Enable magic import"
}
