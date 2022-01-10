OpenTable Embedded PostgreSQL Component
=======================================

Allows embedding PostgreSQL into Java application code with
no external dependencies.  Excellent for allowing you to unit
test with a "real" Postgres without requiring end users to install
and set up a database cluster.

[![Build Status](https://travis-ci.org/opentable/otj-pg-embedded.svg)](https://travis-ci.org/opentable/otj-pg-embedded)

## Basic Usage

In your JUnit test just add (for JUnit 5 example see **Using JUnit5** below):

```java
@Rule
public SingleInstancePostgresRule pg = EmbeddedPostgresRules.singleInstance();
```

This simply has JUnit manage an instance of EmbeddedPostgres (start, stop). You can then use this to get a DataSource with: `pg.getEmbeddedPostgres().getPostgresDatabase();`  

Additionally you may use the [`EmbeddedPostgres`](src/main/java/com/opentable/db/postgres/embedded/EmbeddedPostgres.java) class directly by manually starting and stopping the instance; see [`EmbeddedPostgresTest`](src/test/java/com/opentable/db/postgres/embedded/EmbeddedPostgresTest.java) for an example.

Default username/password is: postgres/postgres and the default database is 'postgres'

## Migrators (Flyway or Liquibase)

You can easily integrate Flyway or Liquibase database schema migration:
##### Flyway
```java
@Rule 
public PreparedDbRule db =
    EmbeddedPostgresRules.preparedDatabase(
        FlywayPreparer.forClasspathLocation("db/my-db-schema"));
```

##### Liquibase
```java
@Rule
public PreparedDbRule db = 
    EmbeddedPostgresRules.preparedDatabase(
            LiquibasePreparer.forClasspathLocation("liqui/master.xml"));
```

This will create an independent database for every test with the given schema loaded from the classpath.
Database templates are used so the time cost is relatively small, given the superior isolation truly
independent databases gives you.

## Postgres version

It is possible to change postgres version:

```java
    EmbeddedPostgres.builder()
        .setTag("10")
        .start();
```

or use custom image:

```java
    EmbeddedPostgres.builder()
        .setImage(DockerImageName.parse("docker.otenv.com/super-postgres"))
        .start();
```


## Using JUnit5

JUnit5 does not have `@Rule`. So below is an example for how to create tests using JUnit5 and embedded postgress, it creates a Spring context and uses JDBI:

```java
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DaoTestUsingJunit5.MockDBConfiguration.class)
class DaoTestUsingJunit5 {
    interface MyDao {}

    @Inject
    MyDao myDao;

    @Test
    void someTest() {
        // ....
    }

    @Import(DaoTestUsingJunit5.GlobalMockDBConfiguration.class)
    @Configuration
    static class MockDBConfiguration {
        @Bean
        public MyDao dao(Jdbi jdbi) {
            return jdbi.onDemand(MyDao.class);
        }
    }

    /**
     * This class is here as inner class for brevity
     * but it's better to have only one for all tests.
     */
    @Configuration
    public static class GlobalMockDBConfiguration {
        @Bean("jdbiUser")
        @Primary
        Jdbi jdbi() throws SQLException {
            DatabasePreparer db = FlywayPreparer.forClasspathLocation("db/migration");

            Jdbi jdbi = Jdbi.create(PreparedDbProvider.forPreparer(db).createDataSource())
                    .installPlugin(new PostgresPlugin())
                    .installPlugin(new SqlObjectPlugin())
                    .setTransactionHandler(new SerializableTransactionRunner());

            return configureJdbi(jdbi);
        }

        static Jdbi configureJdbi(Jdbi jdbi) {
            // possible actions:
            // - register immutables
            // - set up mappers, etc
            return jdbi;
        }
    }
}
```

----
Copyright (C) 2017 OpenTable, Inc
