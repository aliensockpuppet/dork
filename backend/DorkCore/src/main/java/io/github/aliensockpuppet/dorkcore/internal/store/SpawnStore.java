package io.github.aliensockpuppet.dorkcore.internal.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class SpawnStore {
    private final Database db;

    public SpawnStore(Database db) {
        this.db = db;
    }

    public StoredLocation get() throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT world_uid, x, y, z, yaw, pitch FROM spawn WHERE id = 0");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            String uid = rs.getString("world_uid");
            return new StoredLocation(
                    uid == null ? null : UUID.fromString(uid),
                    rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                    (float) rs.getDouble("yaw"), (float) rs.getDouble("pitch"));
        }
    }

    public void set(StoredLocation loc) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO spawn (id, world_uid, x, y, z, yaw, pitch)
                VALUES (0, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    world_uid = excluded.world_uid,
                    x = excluded.x, y = excluded.y, z = excluded.z,
                    yaw = excluded.yaw, pitch = excluded.pitch
                """)) {
            ps.setString(1, loc.worldUid() == null ? null : loc.worldUid().toString());
            ps.setDouble(2, loc.x());
            ps.setDouble(3, loc.y());
            ps.setDouble(4, loc.z());
            ps.setDouble(5, loc.yaw());
            ps.setDouble(6, loc.pitch());
            ps.executeUpdate();
        }
    }

    public boolean isSet() throws SQLException {
        return get() != null;
    }
}
