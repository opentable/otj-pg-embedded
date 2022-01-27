OpenTable Embedded PostgreSQL Component
=======================================

Note: This library requires Java 8+.

Allows embedding PostgreSQL into Java application code, using Docker containers.
Excellent for allowing you to unit
test with a "real" Postgres without requiring end users to install  and set up a database cluster.


Earlier pre 1.x versions used an embedded tarball. This was very very fast, but we switched to a docker based version
for these reasons

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

## Before filing tickets.

1. Before filing tickets, please test your docker environment etc. If using podman or lima instead of "true docker", state so, and realize that the
docker socket api provided by these apps is not 100% compatible, as we've found to our sadness. We'll be revisiting
testing these in the future.
2. No further PRs or tickets will be accepted for the pre 1.0.0 release, unless community support arises for the `legacy` branch.
We recommend those who prefer the embedded tarball use https://github.com/zonkyio/embedded-postgres which was forked a couple
years ago from the embedded branch and is kept reasonably up to date.
3. We primarily use Macs and Ubuntu Linux at OpenTable. We'll be happy to try to help out otherwise, but other platforms, such
as Windows depend primarily on community support.

## Basic Usage

In your JUnit test just add (for JUnit 5 example see **Using JUnit5** below):

```java
@Rule
public SingleInstancePostgresRule pg = EmbeddedPostgresRules.singleInstance();
```

This simply has JUnit manage an instance of EmbeddedPostgres (start, stop). You can then use this to get a DataSource with: `pg.getEmbeddedPostgres().getPostgresDatabase();`  

Additionally you may use the [`EmbeddedPostgres`](src/main/java/com/opentable/db/postgres/embedded/EmbeddedPostgres.java) class directly by manually starting and stopping the instance; see [`EmbeddedPostgresTest`](src/test/java/com/opentable/db/postgres/embedded/EmbeddedPostgresTest.java) for an example.

Default username/password is: postgres/postgres and the default database is 'postgres'

## Sample of Embedded Postgres direct Usage

```
public void testDatabaseName() throws IOException,SQLException{
        EmbeddedPostgres db=EmbeddedPostgres.builder().start();
        Datasource dataSource = db.getPostgresDatabase();
        .... use the datasource then ...
        db.close();
        }
```

The builder includes options to set the image, the tag, the database name, and various configuration options.

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

The default is to use the docker hub registry and pull a tag, hardcoded in `EmbeddedPostgres`. Currently this is "13-latest".

You may change this either by environmental variables or by explicit builder usage

### Environmental Variables

1. If `PG_FULL_IMAGE` is set, then this will be used and is assumed to include the full docker image name. So for example this might be set to `docker.otenv.com/postgres:mytag`
2. Otherwise, if `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX` is set, this is prefixed to "postgres" (adding a slash if it doesn't exist). So for example this might be set to "docker.otenv.com/"
3. Otherwise, the default is used as defined above.

### Explicit builder

It is possible to change postgres image and tag in the builder:

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

## Junit4 is a compile time dependency

This is because TestContainers has a long outstanding bug to remove this - https://github.com/testcontainers/testcontainers-java/issues/970

If you only use Junit5 in your classpath, and bringing in Junit4 bothers you (it does us, sigh), then
you can do the following:

* add maven exclusions to the testcontainers modules you declare dependencies on to strip out junit:junit . This by itself
would lead to NoClassDefFound errors.
* add a dependency on io.quarkus:quarkus-junit4-mock , which imports empty interfaces of the required classes.

We initially excluded junit ourselves, but since you'd still have manually exclude yourself, and this broke junit4 users...

## Docker in Docker, authentication notes

We've been able to get this working in our CICD pipeline with the following

`TESTCONTAINERS_HOST_OVERRIDE=localhost`
`TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX=dockerhub.otenv.com/`

The first parameter corrects for testcontainers getting confused whether to address the hosting container or the "container inside the container".
The second parameter (which outside OpenTable would point to your private Docker Registry) avoids much of the Docker Rate Limiting issues.

By the way, TestContainers does support ~/.docker/config.json for setting authenticated access to Docker, but we've not tested it.

----
Copyright (C) 2017-2022 OpenTable, Inc
