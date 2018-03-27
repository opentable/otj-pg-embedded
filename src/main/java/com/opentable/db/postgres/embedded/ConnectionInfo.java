package com.opentable.db.postgres.embedded;

public class ConnectionInfo {
    private final String dbName;
    private final int port;
    private final String user;

    public ConnectionInfo(final String dbName, final int port, final String user) {
        this.dbName = dbName;
        this.port = port;
        this.user = user;
    }

    public String getUser() {
        return user;
    }

    public String getDbName() {
        return dbName;
    }

    public int getPort() {
        return port;
    }
}
