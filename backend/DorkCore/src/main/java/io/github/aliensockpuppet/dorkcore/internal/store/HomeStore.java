package io.github.aliensockpuppet.dorkcore.internal.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HomeStore {
    private final Database db;

    public HomeStore(Database db) {
        this.db = db;
    }

    public List<String> names(UUID owner) throws SQLException {
        List<String> out = new ArrayList<>();
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement("SELECT name FROM homes WHERE owner = ? ORDER BY rowid")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("name"));
                }
            }
        }
        return out;
    }

    public int count(UUID owner) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM homes WHERE owner = ?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public boolean has(UUID owner, String name) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM homes WHERE owner = ? AND name = ? COLLATE NOCASE")) {
            ps.setString(1, owner.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public StoredLocation get(UUID owner, String name) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT world_uid, x, y, z, yaw, pitch FROM homes WHERE owner = ? AND name = ? COLLATE NOCASE")) {
            ps.setString(1, owner.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    public Map<String, StoredLocation> all(UUID owner) throws SQLException {
        Map<String, StoredLocation> out = new LinkedHashMap<>();
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name, world_uid, x, y, z, yaw, pitch FROM homes WHERE owner = ? ORDER BY rowid")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("name"), read(rs));
                }
            }
        }
        return out;
    }

    public void set(UUID owner, String name, StoredLocation loc) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO homes (owner, name, world_uid, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(owner, name) DO UPDATE SET
                    world_uid = excluded.world_uid,
                    x = excluded.x, y = excluded.y, z = excluded.z,
                    yaw = excluded.yaw, pitch = excluded.pitch
                """)) {
            ps.setString(1, owner.toString());
            ps.setString(2, name);
            ps.setString(3, loc.worldUid() == null ? null : loc.worldUid().toString());
            ps.setDouble(4, loc.x());
            ps.setDouble(5, loc.y());
            ps.setDouble(6, loc.z());
            ps.setDouble(7, loc.yaw());
            ps.setDouble(8, loc.pitch());
            ps.executeUpdate();
        }
    }

    public boolean delete(UUID owner, String name) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM homes WHERE owner = ? AND name = ? COLLATE NOCASE")) {
            ps.setString(1, owner.toString());
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        }
    }

    private static StoredLocation read(ResultSet rs) throws SQLException {
        String uid = rs.getString("world_uid");
        return new StoredLocation(
                uid == null ? null : UUID.fromString(uid),
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                (float) rs.getDouble("yaw"), (float) rs.getDouble("pitch"));
    }
}
