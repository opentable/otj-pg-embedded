package com.opentable.db.postgres.embedded;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

public class FlywayPreparer implements DatabasePreparer {

    private final Flyway flyway;

    public static FlywayPreparer forClasspathLocation(String... locations) {
        Flyway f = new Flyway();
        f.setLocations(locations);
        return new FlywayPreparer(f);
    }

    private FlywayPreparer(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public void prepare(DataSource ds) throws SQLException {
        flyway.setDataSource(ds);
        flyway.migrate();
    }
}
