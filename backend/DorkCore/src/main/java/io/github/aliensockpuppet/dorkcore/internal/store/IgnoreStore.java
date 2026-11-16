package io.github.aliensockpuppet.dorkcore.internal.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class IgnoreStore {
    private final Database db;

    public IgnoreStore(Database db) {
        this.db = db;
    }

    public boolean ignores(UUID owner, UUID target) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM ignores WHERE owner = ? AND ignored = ?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean add(UUID owner, UUID target) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO ignores (owner, ignored) VALUES (?, ?)")) {
            ps.setString(1, owner.toString());
            ps.setString(2, target.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean remove(UUID owner, UUID target) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM ignores WHERE owner = ? AND ignored = ?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, target.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public List<UUID> list(UUID owner) throws SQLException {
        List<UUID> out = new ArrayList<>();
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement("SELECT ignored FROM ignores WHERE owner = ?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.add(UUID.fromString(rs.getString("ignored")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        return out;
    }
}
