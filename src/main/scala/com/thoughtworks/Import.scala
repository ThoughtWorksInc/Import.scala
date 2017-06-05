package com.thoughtworks

import java.io._
import java.net.URI

import scala.reflect.internal.NoPhase
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.AbstractFile
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
final class Import(override val global: Global) extends Plugin {
  import global._

  private final class UriFile(uri: URI) extends AbstractFile {
    override def file: File = null

    override lazy val name: String = new File(path).getName

    override lazy val path: String = uri.getPath

    override def absolute: AbstractFile = this

    override def container: AbstractFile = {
      if (isDirectory) {
        new UriFile(uri.resolve("..").normalize)
      } else {
        new UriFile(uri.resolve(".").normalize)
      }
    }

    override def create(): Unit = unsupported()

    override def delete(): Unit = unsupported()

    override def lastModified: Long = unsupported()

    override def input: InputStream = uri.toURL.openStream

    override def output: OutputStream = unsupported()

    override def iterator: Iterator[AbstractFile] = unsupported()

    override def lookupName(name: String, directory: Boolean): AbstractFile = unsupported()

    override def lookupNameUnchecked(name: String, directory: Boolean): AbstractFile = {
      new UriFile(uri.resolve(if (directory) { name + '/' } else { name }))
    }

    override def isDirectory: Boolean = path.endsWith("/")
  }

  private final class Transformer(currentPhase: GlobalPhase, unit: CompilationUnit) extends global.Transformer {

    private type Handler = (AbstractFile, ImportSelector, Position) => Seq[Tree]

    private val urlOrFileHandler: Handler = { (prefix: AbstractFile, selector: ImportSelector, position: Position) =>
      val ImportSelector(importee, namePos, rename, renamePos) = selector
      val uriString = s"${importee.decodedName.toString}.sc"
      val uri = prefix.lookupPathUnchecked(uriString, directory = false)
      Seq(urlOrFileModule(uri, rename))
    }

    private val execHandler: Handler = { (prefix: AbstractFile, selector: ImportSelector, position: Position) =>
      val ImportSelector(importee, namePos, rename, renamePos) = selector
      if (importee != rename) {
        warning(position.withShift(renamePos), "Renaming is ignored when importing $exec")
      }
      val uriString = s"${importee.decodedName.toString}.sc"
      val uri = prefix.lookupNameUnchecked(uriString, directory = false)
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
            Some(handler -> prefix.lookupNameUnchecked(name.decodedName.toString, directory = true))
          case _ =>
            None
        }
      }
    }

    private def newVirtualFile(name: Name) = {
      val uriString = name.decodedName.toString
      val uri = new URI(uriString)
      val virtualFile = if (uri.isAbsolute) {
        new UriFile(uri)
      } else {
        unit.source.file.container.lookupNameUnchecked(raw"""$uriString.sc""", directory = false)
      }
      virtualFile
    }

    private def newVirtualDirectory(name: Name) = {
      val uriString = name.decodedName.toString
      val uri = new URI(uriString :+ '/')
      val virtualFile = if (uri.isAbsolute) {
        new UriFile(uri)
      } else {
        unit.source.file.container.lookupNameUnchecked(uriString, directory = true)
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
      val q"package $emptyPackage { object temporaryModule { ..$moduleBody } }" = temporaryCompilationUnit.body
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

    private def parse(virtualFile: AbstractFile, headerSize: Int, content: Array[Char]) = {
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

  override val name: String = "magic-import"

  override val components: List[PluginComponent] = List(
    new PluginComponent {
      override val global: Import.this.global.type = Import.this.global

      override val phaseName: String = Import.this.name
      override val runsAfter: List[String] = List("parser")

      override def newPhase(prev: Phase): Phase = new StdPhase(prev) { currentPhase =>
        override def apply(unit: CompilationUnit): Unit = {
          val transformer = new Transformer(currentPhase, unit)
          transformer.transformUnit(unit)
        }
      }
    }
  )
  override val description: String = "Enable magic imports"
}
