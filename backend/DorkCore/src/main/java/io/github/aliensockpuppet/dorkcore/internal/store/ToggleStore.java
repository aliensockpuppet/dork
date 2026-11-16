package io.github.aliensockpuppet.dorkcore.internal.store;

import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class ToggleStore {
    private final Database db;

    public ToggleStore(Database db) {
        this.db = db;
    }

    public Map<PlayerToggles.Toggle, Boolean> getAll(UUID owner) throws SQLException {
        Map<PlayerToggles.Toggle, Boolean> out = new EnumMap<>(PlayerToggles.Toggle.class);
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement("SELECT toggle, state FROM toggles WHERE owner = ?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PlayerToggles.Toggle toggle = parse(rs.getString("toggle"));
                    if (toggle != null) {
                        out.put(toggle, rs.getInt("state") != 0);
                    }
                }
            }
        }
        return out;
    }

    private static PlayerToggles.Toggle parse(String name) {
        try {
            return PlayerToggles.Toggle.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean get(UUID owner, PlayerToggles.Toggle toggle) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT state FROM toggles WHERE owner = ? AND toggle = ?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, toggle.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("state") != 0 : toggle.defaultOn();
            }
        }
    }

    public void set(UUID owner, PlayerToggles.Toggle toggle, boolean on) throws SQLException {
        if (on == toggle.defaultOn()) {
            delete(owner, toggle);
            return;
        }
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO toggles (owner, toggle, state)
                VALUES (?, ?, ?)
                ON CONFLICT(owner, toggle) DO UPDATE SET state = excluded.state
                """)) {
            ps.setString(1, owner.toString());
            ps.setString(2, toggle.name());
            ps.setInt(3, on ? 1 : 0);
            ps.executeUpdate();
        }
    }

    private void delete(UUID owner, PlayerToggles.Toggle toggle) throws SQLException {
        Connection c = db.connection();
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM toggles WHERE owner = ? AND toggle = ?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, toggle.name());
            ps.executeUpdate();
        }
    }
}
