package dev.minesource.sourcechat;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Economía. Moneda interna por defecto (sin dependencias). Si hay un plugin de economía compatible con
 * Vault y economy.provider es auto o vault, se usa su saldo (acceso por reflexión: sin compilar contra Vault).
 */
final class Eco {

    private final SourceChat plugin;
    private Object vault;
    private Method has, balance, withdraw, deposit;
    private boolean tried;

    Eco(SourceChat plugin) {
        this.plugin = plugin;
    }

    private String provider() {
        return plugin.getConfig().getString("economy.provider", "auto").toLowerCase(Locale.ROOT);
    }

    /** Intenta enlazar Vault de forma perezosa (los proveedores se registran después de cargar los plugins). */
    private boolean vault() {
        if (provider().equals("internal")) return false;
        if (vault != null) return true;
        if (tried && !Bukkit.getPluginManager().isPluginEnabled("Vault")) return false;
        tried = true;
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) return false;
            Object prov = rsp.getProvider();
            has = eco.getMethod("has", OfflinePlayer.class, double.class);
            balance = eco.getMethod("getBalance", OfflinePlayer.class);
            withdraw = eco.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            deposit = eco.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            vault = prov;
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    String providerName() {
        return vault() ? "Vault" : "internal";
    }

    long balance(OfflinePlayer p) {
        if (vault()) {
            try {
                return (long) ((Double) balance.invoke(vault, p) + 0.0);
            } catch (Throwable ignored) {
                // cae a la moneda interna
            }
        }
        return plugin.profiles().of(p).coins;
    }

    /** Cobra; false si no alcanza el saldo. */
    boolean withdraw(OfflinePlayer p, long amount) {
        if (amount <= 0) return true;
        if (vault()) {
            try {
                if (!(Boolean) has.invoke(vault, p, (double) amount)) return false;
                Object res = withdraw.invoke(vault, p, (double) amount);
                return (Boolean) res.getClass().getMethod("transactionSuccess").invoke(res);
            } catch (Throwable ignored) {
                // cae a la moneda interna
            }
        }
        Profile pr = plugin.profiles().of(p);
        synchronized (pr) {
            if (pr.coins < amount) return false;
            pr.coins -= amount;
        }
        plugin.profiles().touch();
        return true;
    }

    void deposit(OfflinePlayer p, long amount) {
        if (amount <= 0) return;
        if (vault()) {
            try {
                deposit.invoke(vault, p, (double) amount);
                return;
            } catch (Throwable ignored) {
                // cae a la moneda interna
            }
        }
        Profile pr = plugin.profiles().of(p);
        synchronized (pr) {
            pr.coins += amount;
        }
        plugin.profiles().touch();
    }

    /** Fija el saldo interno (admin). */
    void set(OfflinePlayer p, long amount) {
        Profile pr = plugin.profiles().of(p);
        synchronized (pr) {
            pr.coins = Math.max(0, amount);
        }
        plugin.profiles().touch();
    }

    String fmt(long amount) {
        return amount + " " + plugin.getConfig().getString("economy.symbol", "⛁");
    }
}
