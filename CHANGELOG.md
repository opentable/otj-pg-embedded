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
