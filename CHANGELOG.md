
1.0.0RC2
-------
* Restore Java 8 compatibility
* Update to testcontainers 1.16.3

1.0.0RC1
-----
* A completely rewritten version of `otj-pg-embedded`. Uses "testcontainers" for docker, while preserving API compatibility.

Advantages

* multi arch (m1 etc) support
* Works the same way on every OS - Mac, Windows, Linux. Please note the maintainers only test on Mac Linux
* You need a tarball for every linux distribution as PG 10+ no longer ship a  "universal binary" for linux.
* Easy to switch docker image tag to upgrade versions.
* More maintainable and secure (you can pull docker images you trust, instead of trusting our tarballs)

Admittedly, a few disadvantages

* Slower than running a tarball
* A few compatibility drops and options have probably disappeared. Feel free to submit PRs
* Docker in Docker can be dodgy to get running.

=== legacy tarball versions ===

0.13.4
------
* POM 287, Flyway 7

0.13.3
------
* POM 220, which includes OSS expose-versions maven plugin, solving issues for OSS users
* #124: Separate the handling of the embedded dir in a directory provider interface (PgDirectoryResolver), 
and moves all logic to handle existing bundles from the main class to a separate one 
(UncompressBundleDirectoryResolver ) which implements the PgDirectoryResolver interface.
  
  Eg:
  
  `EmbeddedPostgres pg = EmbeddedPostgres.builder().setPostgresBinaryDirectory(new File("/usr/local")).start();`

0.13.2
------
* Expose contexts for LiquibasePreparer (#106)
* Add OpenJDK 11 to the Travis test matrix and cache dependencies (#118)
* adds optional prefix to logger (#113)
* Issue #116 Server startup fails with "port out of range:-1" error (#117)
* Overwrite existing extracted files if they exist. (#120)
* ProcessOutputLogger: don't prevent jvm shutdown (#123)
* Update POM
* Upgrade to Flyway 6 API (#126)

0.13.1
------
* PR #104 - improved logging names

0.13.0
------
* Postgres 10.6

0.12.11
-------
* PR #84 - Speed up binary extraction.

0.12.10
-------
* Add builder option to override working directory (otherwise uses java.io.tmpdir or the ot.epg.working-dir system properties)

0.12.9
------
* PR #83 - Logging of initDB process

0.12.8
------
* PR #85 - Fixed Caching of Prepared DBs

0.12.7
-----
* PR 97 (Support for Liquibase) 

0.12.6
------
* PR 95 (user in connection uri)
* PR 94 (shorten thread name) applied and reverted, since it required java 9

0.12.5
------
* PR #93 - Add separate Junit5 support directory

0.12.4 
------
* Apache commons upgrade for CVE

0.12.3
------
* Bug forced re-release

0.12.2
------
* Update POM, fixing some build issues.
* Cleanup resulting PMD, Spotbugs issues
* PR #89 - Corrected locale parameter format and detected windows locale

0.12.1
------
* Update README on Windows issue
* PR #79 - add license to POM
* PR #81 - restore lost 'this' reference

0.12.0
-------
* PR #77 - better server customizers
* Postgres 10.3

0.11.4
------
* Exposed getConnectionInfo in PreparedDBRule, PreparedDBProvider. The use case is if you need to access
the port and database name and pass them to a NON-java process. In normal circumstances, you
should just use getDataSource(), which will return a fully ready DataSource for Java.
* Update POM to Spring released versions.

0.11.3
------

* use a non-milestone Spring release, sorry!

0.11.1
------
* fixed output logging bug (immediate close after first read)
* quieted down initialization connect logging

0.11.0
------
* add `setPGStartupWait` method to builder to override the default time (10 seconds) for
waiting for Postgres to start up before throwing an exception.
* fix dataDirectory creation under nested subdirectories
* allow specifying jdbc connection parameters
* configurable startup time

0.10.0
------

* postgres 10
* redirected subprocess output to logger (fixes surefire bug)
* fixed subprocess stderr redirection bug
  (unconditionally using stdout settings)
* latest OT parent pom
* PMD fixups
* improved documentation

< 0.10.0
--------

* ancient history
