package org.jabref.logic.shared;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enumerates all supported database systems (DBMS) by JabRef.
 */
public enum DBMSType {
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s", 5432);

    private final String type;
    private final String driverPath;
    private final String urlPattern;
    private final int defaultPort;

    DBMSType(String type, String driverPath, String urlPattern, int defaultPort) {
        this.type = type;
        this.driverPath = driverPath;
        this.urlPattern = urlPattern;
        this.defaultPort = defaultPort;
    }

    public static Optional<DBMSType> fromString(String typeName) {
        return Arrays.stream(DBMSType.values()).filter(dbmsType -> dbmsType.type.equalsIgnoreCase(typeName)).findAny();
    }

    @Override
    public String toString() {
        return this.type;
    }

    /**
     * @return Java Class path for establishing JDBC connection.
     */
    public String getDriverClassPath() throws Error {
        return this.driverPath;
    }

    /**
     * @return prepared connection URL for appropriate system.
     */
    public String getUrl(String host, int port, String database) {
        return urlPattern.formatted(host, port, database);
    }

    /**
     * Retrieves the port number dependent on the type of the database system.
     */
    public int getDefaultPort() {
        return this.defaultPort;
    }
}
