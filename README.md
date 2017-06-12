OpenTable Embedded PostgreSQL Component
=======================================

Allows embedding PostgreSQL into Java application code with
no external dependencies.  Excellent for allowing you to unit
test with a "real" Postgres without requiring end users to install
and set up a database cluster.

[![Build Status](https://travis-ci.org/opentable/otj-pg-embedded.svg)](https://travis-ci.org/opentable/otj-pg-embedded)

## Basic Usage

In your JUnit test just add:  
```java
@Rule
public EmbeddedPostgreSQLRule pg = new EmbeddedPostgreSQLRule();
```

This simply has JUnit manage an instance of EmbeddedPostgreSQLRule (start, stop). You can then use this to get a DataSource with: `pg.getEmbeddedPostgreSQL().getPostgresDatabase();`  

Additionally you may use the [`EmbeddedPostgres`](src/main/java/com/opentable/db/postgres/embedded/EmbeddedPostgres.java) class directly by manually starting and stopping the instance; see [`EmbeddedPostgresTest`](src/test/java/com/opentable/db/postgres/embedded/EmbeddedPostgresTest.java) for an example.

Default username/password is: postgres/postgres and the default database is 'postgres'

## Flyway Migrator

You can easily integrate Flyway database schema migration:

```java
@Rule
public PreparedDbRule db =
    EmbeddedPostgresRules.preparedDatabase(
        FlywayPreparer.forClasspathLocation("db/my-db-schema"));
```

This will create an independent database for every test with the given schema loaded from the classpath.
Database templates are used so the time cost is relatively small, given the superior isolation truly
independent databases gives you.

## Postgres version

The JAR file contains bundled version of Postgres. You can pass different Postgres version by implementing [`PgBinaryResolver`](src/main/java/com/opentable/db/postgres/embedded/PgBinaryResolver.java).

Example:
```java
class ClasspathBinaryResolver implements PgBinaryResolver {
    public InputStream getPgBinary(String system, String machineHardware) throws IOException {
        ClassPathResource resource = new ClassPathResource(format("pgsql/postgresql-%s-%s.tbz", system, machineHardware));
        return resource.getInputStream();
    }
}

EmbeddedPostgreSQL
            .builder()
            .setPgBinaryResolver(new ClasspathBinaryResolver())
            .start();

```


----
Copyright (C) 2017 OpenTable, Inc
