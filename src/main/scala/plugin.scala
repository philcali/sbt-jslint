package com.github.philcali

import com.googlecode.jslint4java.{
  JSLint,
  JSLintBuilder,
  JSLintResult,
  Issue,
  Option => JSLintOption
}
import JSLintOption._

import com.googlecode.jslint4java.formatter.{
  JSLintResultFormatter,
  PlainFormatter
}

import collection.JavaConversions._

import sbt._
import Keys._

object SbtJSLint extends Plugin {
  import LintKeys._

  object LintKeys {
    lazy val indent = SettingKey[Int](
      "jslint-indent", INDENT.getDescription)

    lazy val maxErrors = SettingKey[Int](
      "jslint-max-errors", MAXERR.getDescription)

    lazy val maxLength = SettingKey[Option[Int]](
      "jslint-max-length", MAXLEN.getDescription)

    lazy val flags = SettingKey[Seq[String]](
      "jslint-flags", "Sequence of optional flags for runtime")

    lazy val listFlags = TaskKey[Unit](
      "jslint-list-flags", "Lists available flags") 

    lazy val initialize = TaskKey[JSLint](
      "jslint-initialize", "Readies a jslint processor"
    )

    lazy val formatter = TaskKey[JSLintResultFormatter](
      "jslint-formatter", "Formats the lint results"
    )

    lazy val jslint = TaskKey[Unit]("jslint")
  }

  def jslintInitialize =
    (indent in jslint, maxErrors in jslint, maxLength in jslint, flags in jslint) map {
      (i, m, l, f) =>
        val builder = new JSLintBuilder()
        val jsl = builder.fromDefault()

        jsl.addOption(INDENT, i.toString)
        jsl.addOption(MAXERR, m.toString)
        l.map(l => jsl.addOption(MAXLEN, l.toString))

        f.map(_.toUpperCase).map(tryOption).foreach(_.map(jsl.addOption))

        jsl
    }

  def tryOption(opt: String) = 
    try { Some(JSLintOption.valueOf(opt)) } catch { case _ => None }

  def jslintTask =
    (streams, unmanagedSources in jslint, initialize in jslint, formatter in jslint) map {
      (s, sources, processor, formatter) =>

      sources.foreach { script =>
        val result = processor.lint(script.name, new java.io.FileReader(script))

        if (result.getIssues.isEmpty) {
          s.log.info("No issues found in %s" format script.name)
        } else {
          s.log.warn(formatter.format(result))
        }
      }
    }

  def jslintListTask = (streams) map { s =>
    val nl = System.getProperty("line.separator")
    val format = (opt: JSLintOption) =>
      " %#10s \t%s".format(opt.getLowerName, opt.getDescription)

    val valid = (opt: JSLintOption) => opt.getDescription.startsWith("If")

    JSLintOption.values.filter(valid).map(format).foreach(println)
  }

  def jslintSources =
    (sourceDirectory in jslint, includeFilter in jslint, excludeFilter in jslint) map {
      (dir, include, exclude) => dir.descendentsExcept(include, exclude).get
    }

  def lintSettingsFor(con: Configuration): Seq[Setting[_]] =
    inConfig(con)(lintSettings0 ++ Seq(
      sourceDirectory in jslint <<= (sourceDirectory in con)(_ / "js")
    ))

  def lintSettings = lintSettingsFor(Compile)

  def lintSettings0: Seq[Setting[_]] = Seq(
    indent in jslint := 4,
    maxErrors in jslint := 50,
    maxLength in jslint := None,
    flags in jslint := Nil,
    includeFilter in jslint := "*.js",
    excludeFilter in jslint <<= excludeFilter in Global,
    unmanagedSources in jslint <<= jslintSources,
    formatter in jslint := (new PlainFormatter),
    initialize <<= jslintInitialize,
    listFlags <<= jslintListTask,
    jslint <<= jslintTask
  )
}
