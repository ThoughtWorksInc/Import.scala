package com.thoughtworks.`import`

import java.io._
import java.net.{URI, URL}
import java.nio.file.Paths

import scala.collection.mutable
import scala.reflect.api.Trees
import scala.reflect.internal.NoPhase
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.{AbstractFile, VirtualFile}
import scala.tools.nsc.{Global, Phase}
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.transform.Transform

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
final class Import(override val global: Global) extends Plugin {

  override val name: String = "magic-import"

  override val components: List[PluginComponent] = List(
    new PluginComponent {
      override val global: Global = Import.this.global
      import global._

      final class UriFile(uri: URI, override val isDirectory: Boolean) extends AbstractFile {
        override def file: File = null

        override lazy val name: String = new File(path).getName

        override lazy val path: String = uri.getPath

        override def absolute: AbstractFile = this

        override def container: AbstractFile = {
          new UriFile(uri.resolve("..").normalize, true)
        }

        override def create(): Unit = unsupported()

        override def delete(): Unit = unsupported()

        override def lastModified: Long = unsupported()

        override def input: InputStream = uri.toURL.openStream

        override def output: OutputStream = unsupported()

        override def iterator: Iterator[AbstractFile] = unsupported()

        override def lookupName(name: String, directory: Boolean): AbstractFile = unsupported()

        override def lookupNameUnchecked(name: String, directory: Boolean): AbstractFile = {
          new UriFile(uri.resolve(name), directory)
        }
      }

      override val phaseName: String = Import.this.name
      override val runsAfter: List[String] = List("parser")

      override def newPhase(prev: Phase): Phase = {
        new StdPhase(prev) { currentPhase =>
          override def apply(unit: CompilationUnit): Unit = {
            val transformer = new Transformer {

              private type Handler = (AbstractFile, ImportSelector, Position) => Seq[Tree]

              private def urlOrFileHandler(prefix: AbstractFile,
                                           selector: ImportSelector,
                                           position: Position): Seq[Tree] = {
                val ImportSelector(importee, namePos, rename, renamePos) = selector
                val uriString = s"${importee.decodedName.toString}.sc"
                val uri = prefix.lookupPathUnchecked(uriString, directory = false)
                Seq(urlOrFileModule(uri, rename))
              }
              private def execHandler(prefix: AbstractFile, selector: ImportSelector, position: Position): Seq[Tree] = {
                val ImportSelector(importee, namePos, rename, renamePos) = selector
                if (importee != rename) {
                  warning(position.withShift(renamePos), "Renaming is ignored when importing $exec")
                }
                val uriString = s"${importee.decodedName.toString}.sc"
                val uri = prefix.lookupPathUnchecked(uriString, directory = false)
                execStats(uri)
              }

              private object UrlPrefix {
                def unapply(selector: Tree): Option[(Handler, AbstractFile)] = {
                  selector match {
                    case q"${Ident(TermName("$exec"))}.$name" =>
                      val virtualFile: AbstractFile = newVirtualDirectory(name)
                      Some(execHandler _ -> virtualFile)
                    case q"${Ident(TermName("$url" | "$file"))}.$name" =>
                      val virtualFile: AbstractFile = newVirtualDirectory(name)
                      Some(urlOrFileHandler _ -> virtualFile)
                    case q"${UrlPrefix(handler, prefix)}.`..`" =>
                      Some(handler -> prefix.container)
                    case q"${UrlPrefix(handler, prefix)}.$name" =>
                      Some(handler -> prefix.lookupPathUnchecked(name.decodedName.toString, directory = true))
                    case _ =>
                      None
                  }
                }
              }

              private def newVirtualFile(name: Name) = {
                val uriString = name.decodedName.toString
                val uri = new URI(uriString)
                val virtualFile = if (uri.isAbsolute) {
                  new UriFile(uri, false)
                } else {
                  unit.source.file.container.lookupPathUnchecked(raw"""$uriString.sc""", directory = false)
                }
                virtualFile
              }

              private def newVirtualDirectory(name: Name) = {
                val uriString = name.decodedName.toString
                val uri = new URI(uriString)
                val virtualFile = if (uri.isAbsolute) {
                  new UriFile(uri, true)
                } else {
                  unit.source.file.container.lookupPathUnchecked(uriString, directory = true)
                }
                virtualFile
              }

              private def execStats(virtualFile: AbstractFile): Seq[Tree] = {
                val writer = new CharArrayWriter()
                writer.append("object temporaryModule {")
                val headerSize = writer.size
                appendFileContent(writer, virtualFile)
                writer.append("\n}\n")
                val content = writer.toCharArray
                val temporaryCompilationUnit = parse(virtualFile, headerSize, content)
                val q"package $emptyPackage { object temporaryModule { ..$moduleBody } }" =
                  temporaryCompilationUnit.body
                moduleBody
              }
              private def urlOrFileModule(virtualFile: AbstractFile, rename: Name): ModuleDef = {
                val writer = new CharArrayWriter()
                writer.append("object ")
                writer.append(rename.encodedName.toString)
                writer.append("{")
                val headerSize = writer.size
                appendFileContent(writer, virtualFile)
                writer.append("\n}\n")
                val content = writer.toCharArray
                val temporaryCompilationUnit = parse(virtualFile, headerSize, content)
                val q"package $emptyPackage { ${moduleDef: ModuleDef} }" = temporaryCompilationUnit.body
                moduleDef
              }

              private def parse(virtualFile: AbstractFile, headerSize: Id, content: Array[Char]) = {
                val batchSourceFile = new BatchSourceFile(virtualFile, content) {
                  override def positionInUltimateSource(position: Position): Position = {
                    position.withShift(-headerSize)
                  }
                }
                val temporaryCompilationUnit = new CompilationUnit(batchSourceFile)

                def applyUntil(phase: GlobalPhase): Unit = {
                  phase.prev match {
                    case null | NoPhase =>
                    case prev: GlobalPhase =>
                      applyUntil(prev)
                  }
                  enteringPhase(phase) {
                    phase.applyPhase(temporaryCompilationUnit)
                  }
                }

                applyUntil(currentPhase)
                temporaryCompilationUnit
              }

              private def appendFileContent(writer: CharArrayWriter, virtualFile: AbstractFile) = {
                val reader = new InputStreamReader(virtualFile.input, scala.io.Codec.UTF8.charSet)
                try {
                  val buffer = Array.ofDim[Char](1500)
                  while ({
                    reader.read(buffer) match {
                      case -1 =>
                        false
                      case size =>
                        writer.write(buffer, 0, size)
                        true
                    }
                  }) {}
                } finally {
                  reader.close()
                }
              }

              override def transformStats(stats: List[Tree], exprOwner: Symbol): List[Tree] = {

                stats.flatMap {
                  case Import(Ident(TermName("$url" | "$file")), selectors) =>
                    selectors.map {
                      case ImportSelector(importee, namePos, rename, renamePos) =>
                        val virtualFile = newVirtualFile(importee)
                        urlOrFileModule(virtualFile, rename)
                    }
                  case Import(Ident(TermName("$exec")), selectors) =>
                    selectors.flatMap {
                      case ImportSelector(importee, namePos, rename, renamePos) =>
                        val virtualFile = newVirtualFile(importee)
                        execStats(virtualFile)
                    }
                  case stat @ Import(UrlPrefix(handler, prefix), selectors) =>
                    selectors.flatMap(handler(prefix, _, stat.pos))
                  case stat =>
                    super.transformStats(List(stat), exprOwner)
                }
              }
            }
            transformer.transformUnit(unit)
          }
        }
      }

    }
  )
  override val description: String = "Enable magic import"
}
