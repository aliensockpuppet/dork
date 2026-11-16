package io.github.aliensockpuppet.dorkcore.internal.store;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private final JavaPlugin plugin;
    private final String url;
    private Connection connection;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "data.db");
        this.url = "jdbc:sqlite:" + file.getPath();
    }

    public void open() throws SQLException {
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create " + dir.getPath());
        }
        try {
            Class.forName("io.github.aliensockpuppet.dorkcore.lib.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver is not on the classpath.", e);
        }
        this.connection = DriverManager.getConnection(url);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=FULL");
            st.execute("PRAGMA foreign_keys=ON");
        }
        createSchema();
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS homes (
                        owner      TEXT    NOT NULL,
                        name       TEXT    NOT NULL COLLATE NOCASE,
                        world_uid  TEXT    NOT NULL,
                        x          REAL    NOT NULL,
                        y          REAL    NOT NULL,
                        z          REAL    NOT NULL,
                        yaw        REAL    NOT NULL,
                        pitch      REAL    NOT NULL,
                        PRIMARY KEY (owner, name)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS spawn (
                        id         INTEGER PRIMARY KEY CHECK (id = 0),
                        world_uid  TEXT    NOT NULL,
                        x          REAL    NOT NULL,
                        y          REAL    NOT NULL,
                        z          REAL    NOT NULL,
                        yaw        REAL    NOT NULL,
                        pitch      REAL    NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS toggles (
                        owner   TEXT    NOT NULL,
                        toggle  TEXT    NOT NULL,
                        state   INTEGER NOT NULL,
                        PRIMARY KEY (owner, toggle)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ignores (
                        owner    TEXT NOT NULL,
                        ignored  TEXT NOT NULL,
                        PRIMARY KEY (owner, ignored)
                    )
                    """);
        }
    }

    public Connection connection() {
        return connection;
    }

    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not close the data file: " + e.getMessage());
        }
    }
}
