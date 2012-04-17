import collection.JavaConversions._

import LintKeys._

seq(lintSettings: _*)

formatter in (Compile, jslintConsoleOutput) := jslintFormat { result => 
  result.getIssues.map{ issue =>
    "line %d: %s" format (issue.getLine, issue.getReason)
  }.mkString("\n")
}

jslintConsoleOutput in Compile <<= (streams, formatter in (Compile, jslintConsoleOutput)) map {
  (s, f) =>
  (results: JSLintResults) => results.foreach { result =>
    val phrase = "%s took %d millis" format(result.getName, result.getDuration)
    val issues = result.getIssues
    if (!issues.isEmpty) {
      s.log.warn("%s. Issues below:" format phrase)
      s.log.warn(f.format(result))
    } else {
      s.log.success(phrase)
    }
  }
}
