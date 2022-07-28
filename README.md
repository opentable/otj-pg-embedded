OpenTable Embedded PostgreSQL Component
=======================================

Note: This library requires Java 8+.

Allows embedding PostgreSQL into Java application code, using Docker containers.
Excellent for allowing you to unit
test with a "real" Postgres without requiring end users to install  and set up a database cluster.

## Recent Changes

The release of 1.0 brings major changes to the innards of this library.
Previous pre 1.x versions used an embedded tarball. This was extremely fast (a major plus), but we switched to a docker based version
for these reasons:

* **Advantages:** 
  * multi architecture support. This has become a huge issue for us with the introduction of the Mac M1 (and Windows ARM, Linux ARM)/
  * The same container works the same way on every OS - Mac, Windows, Linux. 
  * You need a tarball for every linux distribution as PG 10+ no longer ship a "universal binary" for linux. This means a lot of support and maintenance work.
  * Easy to switch docker image tag to upgrade versions - no need for a whole new pg-embedded version.
  * More maintainable and secure (you can pull docker images you trust, instead of trusting our tarballs running in your security context)
  * Trivial to do a build oneself based on the official Postgres image adding extensions, setup scripts etc. - see https://github.com/docker-library/docs/blob/master/postgres/README.md for details.
* **Admittedly, a few disadvantages**
  * Slower than running a tarball (2-5x slower).
  * A few API compatibility changes and options have probably disappeared. Feel free to submit PRs.
  * Docker in Docker can be dodgy to get running. (See below for one thing we discovered)

## Before filing tickets.

1. Before filing tickets, please test your docker environment etc. If using podman or lima instead of "true docker", state so, and realize that the
docker socket api provided by these apps is not 100% compatible, as we've found to our sadness. We'll be revisiting
testing these in the future. We've managed to get PodMan working, albeit not 100% reliably.
2. **No further PRs or tickets will be accepted for the pre 1.0.0 release, unless community support arises for the `legacy` branch.** Please
base any PRs for pre 1.x against the `legacy` branch.
3. We primarily use Macs and Ubuntu Linux at OpenTable. We'll be happy to try to help out otherwise, but other platforms, such
as Windows depend primarily on community support. We simply don't have the time or hardware. Happy to merge PRs though

See "Alternatives Considered" as well if this library doesn't appear to fit your needs.


## Basic Usage

In your JUnit test just add (for JUnit 5 example see **Using JUnit5** below):

```
@Rule
public SingleInstancePostgresRule pg = EmbeddedPostgresRules.singleInstance();
```

This simply has JUnit manage an instance of EmbeddedPostgres (start, stop). You can then use this to get a DataSource with: `pg.getEmbeddedPostgres().getPostgresDatabase();`  

Additionally, you may use the [`EmbeddedPostgres`](src/main/java/com/opentable/db/postgres/embedded/EmbeddedPostgres.java) class directly by manually starting and stopping the instance; see [`EmbeddedPostgresTest`](src/test/java/com/opentable/db/postgres/embedded/EmbeddedPostgresTest.java) for an example.

Default username/password is: postgres/postgres and the default database is 'postgres'

**The port exposed on the host is random and ephemeral so always use the getDatasource, getUrl methods**

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
```
@Rule 
public PreparedDbRule db =
    EmbeddedPostgresRules.preparedDatabase(
        FlywayPreparer.forClasspathLocation("db/my-db-schema"));
```

##### Liquibase
```
@Rule
public PreparedDbRule db = 
    EmbeddedPostgresRules.preparedDatabase(
            LiquibasePreparer.forClasspathLocation("liqui/master.xml"));
```

This will create an independent database for every test with the given schema loaded from the classpath.
Database templates are used so the time cost is relatively small, given the superior isolation truly
independent databases gives you.

## Postgres version

The default is to use the docker hub registry and pull a tag, hardcoded in `EmbeddedPostgres`. Currently, this is "13-latest",
as this fits the needs of OpenTable, however you can change this easily. This is super useful, both to use a newer version
of Postgres, or to build your own DockerFile with additional extensions.

You may change this either by environmental variables or by explicit builder usage

### Environmental Variables

1. If `PG_FULL_IMAGE` is set, then this will be used and is assumed to include the full docker image name. So for example this might be set to `docker.otenv.com/postgres:mytag`
2. Otherwise, if `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX` is set, this is prefixed to "postgres" (adding a slash if it doesn't exist). So for example this might be set to "docker.otenv.com/"
3. Otherwise, the default is used as defined above.

### Explicit builder

It is possible to change postgres image and tag in the builder:

```
    EmbeddedPostgres.builder()
        .setTag("10")
        .start();
```

or use custom image:

```
    EmbeddedPostgres.builder()
        .setImage(DockerImageName.parse("docker.otenv.com/super-postgres"))
        .start();
```

There are also options to set the initDB configuration parameters, or other functional params, the bind mounts, and
the network.

## Using JUnit5

JUnit5 does not have `@Rule`. So below is an example for how to create tests using JUnit5 and embedded postgres, it creates a Spring context and uses JDBI:

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
     * This class is here as inner class for brevity,
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

## Yes, Junit4 is a compile time dependency

This is because TestContainers has a long outstanding bug to remove this -https://github.com/testcontainers/testcontainers-java/issues/970
If you exclude Junit4, you get nasty NoClassDefFound errors.

If you only use Junit5 in your classpath, and bringing in Junit4 bothers you (it does us, sigh), then
you can do the following:

* add maven exclusions to the testcontainers modules you declare dependencies on to strip out junit:junit. This by itself
would still lead to NoClassDefFound errors.
* add a dependency on io.quarkus:quarkus-junit4-mock , which imports empty interfaces of the required classes. This is
a hack and a cheat, but what can you do?

We initially excluded junit4 ourselves, which led to confusing breakages for junit5 users...

## Some new options and some lost from Pre 1.0

* You can't wire to a local postgres, since that concept doesn't make sense here. So that's gone.
* You can add bind mounts and a Network (between two containers), since those are docker concepts, and can
be very useful.
* By the way, TestContainers does support ~/.docker/config.json for setting authenticated access to Docker, but we've not tested it.

## Docker in Docker, authentication notes

We've been able to get this working in our CICD pipeline with the following

```
TESTCONTAINERS_HOST_OVERRIDE=localhost
TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX=dockerhub.otenv.com/
```

The first parameter corrects for testcontainers getting confused whether to address the hosting container or the "container inside the container".
The second parameter (which outside OpenTable would point to your private Docker Registry) avoids much of the Docker Rate Limiting issues. 

See https://github.com/testcontainers/testcontainers-java/issues/4596 for more information

## Alternatives considered

We updated this library primarily for convenience of current users to allow them to make a reasonably smooth transition to a Docker based
test approach.

* Why not just use Testcontainers directly?

You can, and it should work well for you. The builders, the api compatibility, the wrapping around Flyway - that's the added value.
But certainly there's no real reason you can't use TestContainers directly - they have their own Junit4 and Junit5 Rules/Extensions.

* Why not use a maven plugin approach like fabric8-docker-maven?

Honestly I suspect this is a better approach in that it doesn't try to maintain its own version of the Docker API, and
runs outside the tests, reducing issues like forking and threading conflicts. However, it would have been too major an overhaul
for our users.

* "I really prefer the old embedded postgres approach. It's faster."

   We recommend those who prefer the embedded tarball use https://github.com/zonkyio/embedded-postgres which was forked a couple
   of years ago from the embedded branch and is kept reasonably up to date.
   
   Another alternative is Flapdoodle's embedded postgres, but that is deprecated in favor of testcontainers too.

   Both libraries suffer from many of the cons that bedeviled upkeep of this library for years, but they are certainly viable options
   for many.

----
Copyright (C) 2017-2022 OpenTable, Inc
