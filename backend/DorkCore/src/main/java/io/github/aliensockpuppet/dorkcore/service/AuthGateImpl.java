package io.github.aliensockpuppet.dorkcore.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import io.github.aliensockpuppet.dorkcore.api.AuthGate;

import java.lang.reflect.Method;

public final class AuthGateImpl implements AuthGate {
    private static final String[] AUTH_PLUGINS = {"AuthMe"};

    private final boolean failClosedWhenAuthMissing;

    private volatile boolean authMeResolved;
    private volatile Object authMeApi;
    private volatile Method isAuthenticatedMethod;

    public AuthGateImpl(boolean failClosedWhenAuthMissing) {
        this.failClosedWhenAuthMissing = failClosedWhenAuthMissing;
    }

    private AuthGate backing() {
        for (RegisteredServiceProvider<AuthGate> rsp
                : Bukkit.getServicesManager().getRegistrations(AuthGate.class)) {
            AuthGate provider = rsp.getProvider();
            if (provider != this) {
                return provider;
            }
        }
        return null;
    }

    @Override
    public boolean isAuthenticated(Player player) {
        AuthGate backing = backing();
        if (backing != null) {
            return backing.isAuthenticated(player);
        }
        Boolean fromAuthMe = readAuthMe(player);
        if (fromAuthMe != null) {
            return fromAuthMe;
        }
        if (failClosedWhenAuthMissing && authPluginInstalled()) {
            return false;
        }
        return true;
    }

    private Boolean readAuthMe(Player player) {
        if (Bukkit.getPluginManager().getPlugin("AuthMe") == null) {
            return null;
        }
        resolveAuthMe();
        Object api = authMeApi;
        Method method = isAuthenticatedMethod;
        if (api == null || method == null) {
            return null;
        }
        try {
            Object result = method.invoke(api, player);
            return result instanceof Boolean b ? b : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private void resolveAuthMe() {
        if (authMeResolved) {
            return;
        }
        synchronized (this) {
            if (authMeResolved) {
                return;
            }
            try {
                Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                Method method = apiClass.getMethod("isAuthenticated", Player.class);
                this.authMeApi = api;
                this.isAuthenticatedMethod = method;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                this.authMeApi = null;
                this.isAuthenticatedMethod = null;
            } finally {
                this.authMeResolved = true;
            }
        }
    }

    private static boolean authPluginInstalled() {
        for (String name : AUTH_PLUGINS) {
            if (Bukkit.getPluginManager().getPlugin(name) != null) {
                return true;
            }
        }
        return false;
    }
}
