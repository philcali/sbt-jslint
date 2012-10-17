import collection.JavaConversions._

import LintKeys._

seq(lintSettings: _*)

flags in (Compile, jslint) += "sloppy"

predefs in (Compile, jslint) := Seq("predefinedGlobal1", "predefinedGlobal2")

formatter in (Compile, jslintConsoleOutput) <<=
  (sourceDirectory in (Compile, jslint)) (ShortFormatter)

formatter in (Compile, jslint) := jslintFormat { result =>
  result.getIssues.map { issue =>
    "line %d: %s" format (issue.getLine, issue.getReason)
  }.mkString("\n")
}

outputs in (Compile, jslint) <+= (streams, formatter in (Compile, jslint)) map {
  (s, f) =>
  (results: JSLintResults) => results.foreach { result =>
    val file = result.getName.split(System.getProperty("file.separator")).last
    val phrase = "%s took %d millis" format(file, result.getDuration)
    val issues = result.getIssues
    if (!issues.isEmpty) {
      s.log.warn("%s. Issues below:" format phrase)
      s.log.warn(f.format(result))
    } else {
      s.log.success(phrase)
    }
  }
}
