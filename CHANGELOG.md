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
