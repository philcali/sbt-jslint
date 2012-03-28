# SBT JSLint Plugin

This sbt plugin simply wraps [jslint4java][1] with is a Java implementation
of [jslint][2].

You can format the jslint issues anyway you want.

__Note__: will only be published for sbt version >= 0.11.2.

## Installation

In your `project/plugins.sbt`, simply add the following line:

`addSbtPlugin("com.github.philcali" % "sbt-jslint" % "0.1.0")`

Then, include in your build:

`seq(lintSettings: _*)`

## SBT Settings and Tasks

```
jslint # Runs jslint with the options specified in jslint-flags
jslint-list-flags # Lists all the available flags
jslint-with <flag-1> <flag-n> # Runs JSlint on inputed flags
jslint-indent(for jslint) # Spaces
jslint-max-errors(for jslint) # Issue threshold
jslint-max-length(for jslint) # Column width for javascript file
jslint-flags(for jslint) # All the other flags for jslint
jslint-initialize(for jslint) # Builds the JSLint processor
jslint-formatter(for jslint) # Format the results in sbt
unmanaged-sources(for jslint) # Js files to run jslint on
include-filter(for jslint) # Run jslint on these files
exclude-filter(for jslint) # Exclude these files
```

## Some Notes

This plugin makes no assumptions about default flags, other than the ones listed
as default on [jslint][2]:

- `LintKeys.indent in LintKeys.jslint := 4`
- `LintKeys.maxErrors in LintKeys.jslint := 50`
- `LintKeys.maxLength in LintKeys.jslint := None`
- `LintKeys.flags in LintKeys.jslint := Seq("sloppy")`

Because there are so many lint flags, simply add the lint flag keys to
`lint-flags` or:

```
LintKeys.flags in (Compile, LintKeys.jslint) ++= Seq("browser", "on", "anon")
```

Use `jslint-list-flags` to print out a list of available flags to be included:

```
> jslint-list-flags     
       anon     If the space may be omitted in anonymous function declarations
    bitwise     If bitwise operators should be allowed
    browser     If the standard browser globals should be predefined
        cap     If upper case html should be allowed
   continue     If the continuation statement should be tolerated
        css     If css workarounds should be tolerated
      debug     If debugger statements should be allowed
      devel     If logging should be allowed (console, alert, etc.)
       eqeq     If == should be allowed
        es5     If es5 syntax should be allowed
       evil     If eval should be allowed
      forin     If for in statements need not filter
   fragment     If html fragments should be allowed
     newcap     If constructor names capitalization is ignored
       node     If node.js globals should be predefined
      nomen     If names may have dangling _
         on     If html event handlers should be allowed
   passfail     If the scan should stop on first error
   plusplus     If increment/decrement should be allowed
 properties     If all property names must be declared with /*properties*/
     regexp     If the . should be allowed in regexp literals
      rhino     If the rhino environment globals should be predefined
     sloppy     If the 'use strict'; pragma is optional
        sub     If all forms of subscript notation are tolerated
      undef     If variables can be declared out of order
    unparam     If unused parameters should be tolerated
       vars     If multiple var statements per function should be allowed
      white     If sloppy whitespace is tolerated
     widget     If the yahoo widgets globals should be predefined
    windows     If ms windows-specific globals should be predefined
```

## Per-run Flags

When working with jslint, you may initially want less flags as a
catch all, and get more specific as you work out the obvious issues, like
adding `undef` for files that depend on jquery. This plugin supports this
work flow, with `jslint-with <flag>`.

The `jslint-with` input task auto-completes the available flag with tab.
Running jslint in this manner, will load your predefined flags in `jslint-flags`,
and your newly defined flags.

## Mixing Settings

This plugin also makes no assumption about how to integrate it in your build.
If you wish to apply `jslint` to a specific configuration, then use
`lintSettingsFor(configuration)`.

If you want jslint to be run with test, yet pulled for `Compile` resources.
Something like that would look like this:

```
import sbtjslint.Plugin.LintKeys._

val settings: Seq[Setting[_]] = lintSettings ++ lintSettingsFor(Test) ++ Seq(
  unmanagedSources in jslint <<= unmanagedSources in (Compile, jslint),
  compile in Test <<= (compile in Test).dependsOn(jslint)
)
```

## Custom Formatting

Currently, jslint results are outputted to the shell as warnings, because
errors in javascript are determined in runtime.

By default, the plugin uses the `PlainFormatter` provided with the library, but
this is configurable. The plugin has an additional formatter called
`ShortFormatter`, that simply displays the issue count rather than the details.

`LintKeys.formatter in (Compile, LintKeys.jslint) := ShortFormatter`

This _setting_ is actually an sbt task, which gives a custom formatter
access to the `TaskStreams` and the current `State` among other useful
reporting information.

## License

MIT

[1]: https://github.com/happygiraffe/jslint4java
[2]: http://jslint.com/
