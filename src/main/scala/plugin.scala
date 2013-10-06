package sbtjslint

import util.control.Exception.allCatch

import com.googlecode.jslint4java.{
  JSLint,
  JSLintBuilder,
  JSLintResult,
  Issue,
  Option => JSLintOption
}
import JSLintOption._

import com.googlecode.jslint4java.formatter.{
  JSLintResultFormatter => ResultFormatter,
  PlainFormatter,
  JSLintXmlFormatter
}

import collection.JavaConversions._

import sbt._
import Keys._

import complete.DefaultParsers._

object Plugin extends sbt.Plugin {
  import LintKeys._

  type JSLintResults = Seq[JSLintResult]
  type JSLintOutput = (JSLintResults => Unit)

  case class ShortFormatter(source: File) extends ResultFormatter {
    def header = null
    def footer = null

    def format(result: JSLintResult) = {
      val count = result.getIssues.size
      val word = if (count == 1) "issue" else "issues"

      val relPath = result.getName.replace(source.toString(), ".")
      val padded = if (relPath.length > 35) {
        "..." + relPath.takeRight(32).mkString
      } else {
        relPath
      }

      "%-35s | %d %s found." format (padded, result.getIssues.size, word)
    }
  }

  object LintKeys {
    lazy val indent = SettingKey[Int](
      "jslint-indent", INDENT.getDescription)

    lazy val maxErrors = SettingKey[Int](
      "jslint-max-errors", MAXERR.getDescription)

    lazy val maxLength = SettingKey[Option[Int]](
      "jslint-max-length", MAXLEN.getDescription)

    lazy val flags = SettingKey[Seq[String]](
      "jslint-flags", "Sequence of optional flags for runtime")

    lazy val predefs = SettingKey[Seq[String]](
      "jslint-predefs", "Sequence of optional flags for runtime")

    lazy val formatter = SettingKey[ResultFormatter](
      "jslint-formatter", "Formats the lint results"
    )

    lazy val explode = SettingKey[Boolean](
      "jslint-explode", "Finding the first issue throw a build exception"
    )

    lazy val outputs = TaskKey[Seq[JSLintOutput]](
      "jslint-outputs", "List of ouputs to be used"
    )

    lazy val initialize = TaskKey[JSLint](
      "jslint-initialize", "Readies a jslint processor"
    )

    lazy val jslintConsoleOutput = TaskKey[JSLintOutput](
      "jslint-console-output", "Outputs lint results in console"
    )

    lazy val jslintFileOutput = TaskKey[JSLintOutput](
      "jslint-file-output", "Outputs lint results to target(for jslint-file-output)"
    )

    lazy val jslint = TaskKey[Unit]("jslint")

    lazy val listFlags = TaskKey[Unit](
      "jslint-list-flags", "Lists available flags"
    )

    lazy val jslintInput = InputKey[Unit](
      "jslint-with", "Run jslint with input flags"
    )
  }

  def jslintFormat(fun: JSLintResult => String) = new ResultFormatter {
    def header = null
    def footer = null
    def format(result: JSLintResult) = fun(result)
  }

  private def jslintInitialize =
    (indent in jslint, maxErrors in jslint,
     maxLength in jslint, flags in jslint, predefs in jslint) map {
      (i, m, l, f, p) =>
        val builder = new JSLintBuilder()
        val jsl = builder.fromDefault()

        jsl.addOption(INDENT, i.toString)
        jsl.addOption(MAXERR, m.toString)
        l.map(l => jsl.addOption(MAXLEN, l.toString))

        f.map(tryOption).foreach(_.map(jsl.addOption))
        jsl.addOption(PREDEF, p.mkString(","))

        jsl
    }

  private def tryOption(opt: String) =
    allCatch.opt(JSLintOption.valueOf(opt.toUpperCase))

  private def validOptions(opt: JSLintOption) = opt.getDescription.startsWith("If")

  private def jslintTask =
    (streams, explode in jslint, sourceDirectory in jslint,
     unmanagedSources in jslint, initialize in jslint,
     outputs in jslint, jslintFileOutput in jslint) map (performLint)

  private def jslintConsoleOutputTask =
    (streams, formatter in jslintConsoleOutput, sourceDirectory in jslint) map {
      (s, f, d) => (results: JSLintResults) => {
        results.foreach { result =>
          if (result.getIssues.isEmpty) {
            val relPath = result.getName.replace(d.toString, ".")
            val shortened = if (relPath.length > 32) {
              "..." + relPath.takeRight(29).mkString
            } else {
              relPath
            }

            s.log.success("%-32s | No issues found." format shortened)
          } else {
            s.log.warn(f.format(result))
          }
        }
      }
    }

  private def jslintFileOutputTask =
    (streams, target in jslintFileOutput, formatter in jslintFileOutput) map {
      (s, t, f) => (results: JSLintResults) => {
        val nl = System.getProperty("line.separator")

        val header = if (f.header == null) "" else f.header + nl
        val footer = if (f.footer == null) "" else nl + f.footer
        val formatted = results.filter(!_.getIssues.isEmpty).map(f.format).mkString(nl)

        IO.write(t, header + formatted + footer)

        s.log.info("Output results to %s" format t.toString())
      }
    }

  private def performLint(
    s: TaskStreams, e: Boolean, d: File, fs: Seq[File],
    p: JSLint, outs: Seq[JSLintOutput], fo: JSLintOutput) = {
    s.log.info("Performing jslint in %s..." format d.toString())
    val rs = fs.map {
      f => p.lint(f.toString, new java.io.FileReader(f))
    }
    if (e) {
      val allCount = (0 /: rs) { case (i, res) =>
        val count = (0 /: res.getIssues) {
          case (n, r) if n < 4 => s.log.error(r.toString); n + 1
          case (n, _) => n + 1
        }
        if (count > 0 && count - 4 > 0)
          s.log.error("%s: Found %d more..." format (res.getName, count - 4))
        i + count
      }

      if (allCount > 0) {
        fo(rs)
        throw new RuntimeException("Found %d total" format allCount)
      }
    }
    outs.foreach(_.apply(rs))
  }

  private def jslintListTask = (streams) map { s =>
    val format = (opt: JSLintOption) =>
      " %#10s \t%s".format(opt.getLowerName, opt.getDescription)

    JSLintOption.values.filter(validOptions).map(format).foreach(println)
  }

  private def jslintSources =
    (sourceDirectory in jslint, includeFilter in jslint, excludeFilter in jslint) map {
      (dir, include, exclude) => dir.descendantsExcept(include, exclude).get
    }

  private val flagParser = (state: State) => {
    val keys = JSLintOption.values.filter(validOptions).map(_.getLowerName)

    Space ~> keys.map(key => token(key)).reduceLeft(_ | _) +
  }

  private val jslintInputTask = (parsed: TaskKey[Seq[String]]) => {
    (parsed, streams, explode in jslint, sourceDirectory in jslint,
     unmanagedSources in jslint, initialize in jslint,
     outputs in jslint, jslintFileOutput in jslint) map {
      (opts, s, e, dir, sources, lint, outs, fo) =>

        opts.map(tryOption).foreach(_.map(lint.addOption))

        performLint(s, e, dir, sources, lint, outs, fo)
    }
  }

  def lintSettingsFor(con: Configuration): Seq[Setting[_]] =
    inConfig(con)(lintSettings0 ++ Seq(
      sourceDirectory in jslint <<= (sourceDirectory in con)(_ / "js"),
      watchSources in Global <++= (unmanagedSources in jslint),
      // Outputs for configuration
      outputs in jslint <++= (jslintConsoleOutput, jslintFileOutput) map (Seq(_, _))
    ))

  def lintSettings = lintSettingsFor(Compile)

  def lintSettings0: Seq[Setting[_]] = Seq(
    indent in jslint := 4,
    maxErrors in jslint := 50,
    maxLength in jslint := None,
    flags in jslint := Nil,
    predefs in jslint := Nil,
    explode in jslint := false,
    includeFilter in jslint := "*.js",
    excludeFilter in jslint <<= excludeFilter in Global,
    unmanagedSources in jslint <<= jslintSources,
    // Setup console output
    jslintConsoleOutput <<= jslintConsoleOutputTask,
    formatter in jslintConsoleOutput := (new PlainFormatter),
    // Setup File output
    jslintFileOutput <<= jslintFileOutputTask,
    formatter in jslintFileOutput := (new JSLintXmlFormatter),
    target in jslintFileOutput <<= (target) (_ / "jslint" / "results.xml"),
    cleanFiles <+= target in jslintFileOutput,
    // No outputs for global
    outputs in jslint := Nil,
    // Lint tasks
    initialize <<= jslintInitialize,
    listFlags <<= jslintListTask,
    jslint <<= jslintTask,
    jslintInput <<= InputTask(flagParser)(jslintInputTask)
  )
}
