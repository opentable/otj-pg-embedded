0.10.1
------
* add `setPGStartupWait` method to builder to override the default time (10 seconds) for
waiting for Postgres to start up before throwing an exception.

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
