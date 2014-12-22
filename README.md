OpenTable Embedded PostgreSQL Component
=======================================

Allows embedding PostgreSQL into Java application code with
no external dependencies.  Excellent for allowing you to unit
test with a "real" Postgres without requiring end users to install
and set up a database cluster.

[![Build Status](https://travis-ci.org/opentable/otj-pg-embedded.svg)](https://travis-ci.org/opentable/otj-pg-embedded)

Basic Usage
-----------

In your JUnit test just add:  
```java
@Rule
public EmbeddedPostgreSQLRule pg = new EmbeddedPostgreSQLRule();
```

This simply has JUnit manage an instance of EmbeddedPostgreSQLRule (start, stop). You can then use this to get a DataSource with: `pg.getEmbeddedPostgreSQL().getPostgresDatabase();`  

Additionally you may use the [`EmbeddedPostgreSQL`](src/main/java/com/opentable/db/postgres/embedded/EmbeddedPostgreSQL.java) class directly by manually starting and stopping the instance; see [`EmbeddedPostgreSQLTest`](src/test/java/com/opentable/db/postgres/embedded/EmbeddedPostgreSQLTest.java) for an example.  

----
Copyright (C) 2014 OpenTable, Inc
