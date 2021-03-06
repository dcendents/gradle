## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### More convenient testing of Gradle plugins

Gradle 2.6 introduced the “[Gradle TestKit](userguide/test_kit.html)” which made it easier to thoroughly test Gradle plugins.
The TestKit has improved and matured with subsequent Gradle releases.

Starting with Gradle 2.13, less manual build configuration is required in order to make your plugin implementation available to test builds.
The [Java Gradle Plugin Development Plugin](userguide/javaGradle_plugin.html) now integrates with the TestKit by making the plugin under test's implementation classpath discoverable at test time.

See the [TestKit chapter in the Gradle User Guide](userguide/test_kit.html#sub:test-kit-automatic-classpath-injection) for more about this new feature.

### Customizing the HTML reports of Checkstyle and FindBugs

The HTML reports generated by the Checkstyle and FindBugs plugins can now be customized with local XSLT stylesheets.
See the documentation for the [Checkstyle](userguide/checkstyle_plugin.html) and [FindBugs](userguide/findbugs_plugin.html)
plugins for more details. Note that sample stylesheets for both plugins are available at:

- Checkstyle: [https://github.com/checkstyle/contribution/tree/master/xsl](https://github.com/checkstyle/contribution/tree/master/xsl)
- Findbugs: [https://github.com/findbugsproject/findbugs/tree/master/findbugs/src/xsl](https://github.com/findbugsproject/findbugs/tree/master/findbugs/src/xsl)

### Support for Groovydoc's noTimestamp and noVersionStamp flags

Groovy 2.4.6 adds two new flags to its Groovydoc command.
Using these flags produce Groovydoc HTML pages without timestamp or Groovy version stamp information embedded within them.
This means that it is harder to determine when or which version of Groovy produced the pages but means the pages remain totally unchanged across builds unless the actual content changes.

Two properties are added and can be used as follows.

    groovydoc {
        noTimestamp = true
        noVersionStamp = true
    }

The flags are ignored for versions of Groovy prior to 2.4.6.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

<!--
### Example breaking change
-->

### Deleting no longer follows symlinks.

The Delete task will no longer follow symlinks by default and project.delete() will not follow symlinks at all. Previous versions of Gradle would follow-symlinks during deletions. If you need the Delete
task to follow symlinks set `followSymlinks = true`. If you need `project.delete()` to follow symlinks, replace it with `ant.delete()`. This was done to prevent issues where Gradle would attempt to delete files
outside of Gradle's build directory (e.g. NPM installed in a user-writeable location.).

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Alexander Afanasyev](https://github.com/cawka) - allow configuring java.util.logging in tests ([GRADLE-2524](https://issues.gradle.org/browse/GRADLE-2524))
* [Evgeny Mandrikov](https://github.com/Godin) - upgrade default JaCoCo version to 0.7.6
* [Randall Becker](https://github.com/rsbecker) - bypass ulimit in NONSTOP os
* [Endre Fejese](htts://github.com/fejese) - fix a typo in a javadoc comment
* [Thomas Broyer](https://github.com/tbroyer) - add design doc for better/built-in Java annotation processing support
* [Ethan Hall](https://github.com/ethankhall) - make Delete tasks not follow symlinks ([GRADLE-2892](https://issues.gradle.org/browse/GRADLE-2892))
* [Alpha Hinex](https://github.com/alphahinex) - upgrade to Ant 1.9.6
* [Denis Krapenko](https://github.com/dkrapenko) - support multiple values for some command-line options
* [P. P.](https://github.com/pepoirot) - support for stylesheets with FindBugs and Checkstyle
* [Maciej Kowalski](https://github.com/fkowal) - move `DEFAULT_JVM_OPTS` after `APP_HOME` in application plugin
* [Paul King](https://github.com/paulk-asert) - support groovydoc's `noTimestamp` and `noVersionStamp` properties
* [Schalk Cronjé](https://github.com/ysb33r) - update docs about `getFile()` and filters
* [Oliver Reissig](https://github.com/oreissig) - improve error message when `tools.jar` is not found
* [Andrew Reitz](https://github.com/pieces029) - fix a broken link to the groovy documentation
* [Bryan Bess](https://github.com/squarejaw) - fix documentation layout for scala and groovy plugins
* [Jeffrey Crowell](https://github.com/crowell) - upgrade Apache Commons Collections to v3.2.2
* [Sebastian Schuberth](https://github.com/sschuberth) - rich console output for Cygwin's mintty
* [Guillaume Laforge](https://github.com/glaforge) - remove extraneous `public` keywords from build.gradle
* [Jeremie Jost](https://github.com/jjst) - fix a typo in the application plugin documentation

<!--
* [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
