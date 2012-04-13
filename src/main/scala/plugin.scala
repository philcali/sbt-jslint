package sbtjslint

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
  PlainFormatter
}

import collection.JavaConversions._

import sbt._
import Keys._

import complete.DefaultParsers._

object Plugin extends sbt.Plugin {
  import LintKeys._

  object ShortFormatter extends ResultFormatter {
    def header = null
    def footer = null

    def format(result: JSLintResult) = {
      val count = result.getIssues.size
      val word = if (count == 1) "issue" else "issues"

      "Found %d %s." format (result.getIssues.size, word)
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

    lazy val initialize = TaskKey[JSLint](
      "jslint-initialize", "Readies a jslint processor"
    )

    lazy val formatter = TaskKey[ResultFormatter](
      "jslint-formatter", "Formats the lint results"
    )

    lazy val jslint = TaskKey[Unit]("jslint")

    lazy val listFlags = TaskKey[Unit](
      "jslint-list-flags", "Lists available flags"
    )

    lazy val jslintInput = InputKey[Unit](
      "jslint-with", "Run jslint with input flags"
    )
  }

  def jslintInitialize =
    (indent in jslint, maxErrors in jslint,
     maxLength in jslint, flags in jslint) map {
      (i, m, l, f) =>
        val builder = new JSLintBuilder()
        val jsl = builder.fromDefault()

        jsl.addOption(INDENT, i.toString)
        jsl.addOption(MAXERR, m.toString)
        l.map(l => jsl.addOption(MAXLEN, l.toString))

        f.map(tryOption).foreach(_.map(jsl.addOption))

        jsl
    }

  def tryOption(opt: String) =
    try { Some(JSLintOption.valueOf(opt.toUpperCase)) } catch { case _ => None }

  def validOptions(opt: JSLintOption) = opt.getDescription.startsWith("If")

  def jslintTask =
    (streams,
     sourceDirectory in jslint,
     unmanagedSources in jslint,
     initialize in jslint,
     formatter in jslint) map (performLint)

  def performLint(s: TaskStreams, sourceDir: File, d: Seq[File], p: JSLint, f: ResultFormatter) {
    s.log.info("Performing jslint in %s..." format (sourceDir.toString()))
    d.foreach { script =>
      val result = p.lint(script.name, new java.io.FileReader(script))

      if (result.getIssues.isEmpty) {
        s.log.success("No issues found in %s" format script.name)
      } else {
        s.log.warn(f.format(result))
      }
    }
  }

  def jslintListTask = (streams) map { s =>
    val nl = System.getProperty("line.separator")
    val format = (opt: JSLintOption) =>
      " %#10s \t%s".format(opt.getLowerName, opt.getDescription)

    JSLintOption.values.filter(validOptions).map(format).foreach(println)
  }

  def jslintSources =
    (sourceDirectory in jslint, includeFilter in jslint, excludeFilter in jslint) map {
      (dir, include, exclude) => dir.descendentsExcept(include, exclude).get
    }

  val flagParser = (state: State) => {
    val keys = JSLintOption.values.filter(validOptions).map(_.getLowerName)

    Space ~> keys.map(key => token(key)).reduceLeft(_ | _) +
  }

  val jslintInputTask = (parsed: TaskKey[Seq[String]]) => {
    (parsed, streams, sourceDirectory in jslint, unmanagedSources in jslint,
     initialize in jslint, formatter in jslint) map {
      (opts, s, dir, sources, lint, formatter) =>

        opts.map(tryOption).foreach(_.map(lint.addOption))

        performLint(s, dir, sources, lint, formatter)
    }
  }

  def lintSettingsFor(con: Configuration): Seq[Setting[_]] =
    inConfig(con)(lintSettings0 ++ Seq(
      sourceDirectory in jslint <<= (sourceDirectory in con)(_ / "js"),
      watchSources in jslint <<= (unmanagedSources in jslint)
    ))

  def lintSettings = lintSettingsFor(Compile)

  def lintSettings0: Seq[Setting[_]] = Seq(
    indent in jslint := 4,
    maxErrors in jslint := 50,
    maxLength in jslint := None,
    flags in jslint := Seq("sloppy"),
    includeFilter in jslint := "*.js",
    excludeFilter in jslint <<= excludeFilter in Global,
    unmanagedSources in jslint <<= jslintSources,
    formatter in jslint := (new PlainFormatter),
    initialize <<= jslintInitialize,
    listFlags <<= jslintListTask,
    jslint <<= jslintTask,
    jslintInput <<= InputTask(flagParser)(jslintInputTask)
  )
}
