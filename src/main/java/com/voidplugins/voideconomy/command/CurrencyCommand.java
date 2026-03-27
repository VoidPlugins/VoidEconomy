package com.voidplugins.voideconomy.command;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import com.voidplugins.voideconomy.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CurrencyCommand extends Command {

    private static final Set<String> ADMIN_ACTIONS = Set.of(
            "give", "add", "take", "remove", "set", "reset", "clear"
    );
    private static final Set<String> AMOUNT_ACTIONS = Set.of("give", "add", "take", "remove", "set");

    private final VoidEconomy plugin;
    private final Currency currency;

    public CurrencyCommand(VoidEconomy plugin, Currency currency) {
        super(currency.getCommand());
        this.plugin = plugin;
        this.currency = currency;
        setDescription("View or manage " + currency.getDisplayName() + " balances.");
        setUsage("/" + currency.getCommand() + " [player | <action> <player> [amount]]");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            // /<currency> → own balance
            if (!(sender instanceof Player player)) {
                sender.sendMessage(msg("console-must-specify-player"));
                return true;
            }
            showBalance(sender, player.getUniqueId(), player.getName(), true);
            return true;
        }

        String first = args[0].toLowerCase();

        if (ADMIN_ACTIONS.contains(first)) {
            // /<currency> <action> <player> [amount]
            handleAdminAction(sender, first, args);
            return true;
        }

        // /<currency> <player> → view their balance
        if (!sender.hasPermission("voideconomy." + currency.getId() + ".balance.others")) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        resolvePlayer(sender, args[0]).thenAccept(target -> {
            if (target == null) return;
            Bukkit.getScheduler().runTask(plugin,
                    () -> showBalance(sender, target.uuid(), target.name(), false));
        });
        return true;
    }

    private void handleAdminAction(CommandSender sender, String action, String[] args) {
        String perm = "voideconomy." + currency.getId() + "." + action;
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(msg("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtil.colorize("&cUsage: /" + currency.getCommand() + " " + action
                    + " <player>" + (AMOUNT_ACTIONS.contains(action) ? " <amount>" : "")));
            return;
        }

        boolean needsAmount = AMOUNT_ACTIONS.contains(action);
        if (needsAmount && args.length < 3) {
            sender.sendMessage(MessageUtil.colorize("&cUsage: /" + currency.getCommand() + " " + action + " <player> <amount>"));
            return;
        }

        double amount = 0;
        if (needsAmount) {
            try {
                amount = Double.parseDouble(args[2]);
                if (amount <= 0 && !action.equals("set")) {
                    sender.sendMessage(msg("invalid-amount"));
                    return;
                }
                if (amount < 0) {
                    sender.sendMessage(msg("invalid-amount"));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(msg("invalid-amount"));
                return;
            }
        }

        final double finalAmount = amount;
        resolvePlayer(sender, args[1]).thenAccept(target -> {
            if (target == null) return;
            applyAction(sender, action, target, finalAmount);
        });
    }

    private void applyAction(CommandSender sender, String action, ResolvedPlayer target, double amount) {
        UUID uuid = target.uuid();
        String name = target.name();
        var api = plugin.getAPI();

        switch (action) {
            case "give", "add" -> {
                api.give(uuid, currency.getId(), amount)
                        .thenAccept(newBal -> Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(currencyMsg("given", name, amount, newBal));
                            notifyTarget(uuid, name, "received-give", amount, newBal);
                        }));
            }
            case "take", "remove" -> {
                api.remove(uuid, currency.getId(), amount)
                        .thenAccept(newBal -> Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(currencyMsg("taken", name, amount, newBal));
                            notifyTarget(uuid, name, "received-take", amount, newBal);
                        }));
            }
            case "set" -> {
                if (currency.hasMaxBalance() && amount > currency.getMaxBalance()) {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg("amount-too-large")));
                    return;
                }
                api.set(uuid, currency.getId(), amount)
                        .thenRun(() -> Bukkit.getScheduler().runTask(plugin,
                                () -> sender.sendMessage(currencyMsg("set", name, amount, amount))));
            }
            case "reset" -> {
                double def = currency.getDefaultBalance();
                api.reset(uuid, currency.getId())
                        .thenRun(() -> Bukkit.getScheduler().runTask(plugin,
                                () -> sender.sendMessage(currencyMsg("reset", name, def, def))));
            }
            case "clear" -> {
                api.clear(uuid, currency.getId())
                        .thenRun(() -> Bukkit.getScheduler().runTask(plugin,
                                () -> sender.sendMessage(currencyMsg("cleared", name, 0, 0))));
            }
        }
    }

    private void showBalance(CommandSender sender, UUID uuid, String name, boolean isSelf) {
        plugin.getPlayerStore().getBalance(uuid, name, currency.getId())
                .thenAccept(balance -> {
                    String key = isSelf ? "balance-self" : "balance-other";
                    String message = currencyMsg(key, name, balance, balance);
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
                });
    }

    private void notifyTarget(UUID uuid, String name, String msgKey, double amount, double newBal) {
        Player target = Bukkit.getPlayer(uuid);
        if (target != null) {
            target.sendMessage(currencyMsg(msgKey, name, amount, newBal));
        }
    }

    // ── Player resolution (checks online → cached offline → database) ─────────

    private CompletableFuture<ResolvedPlayer> resolvePlayer(CommandSender sender, String name) {
        // 1. Online
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(new ResolvedPlayer(online.getUniqueId(), online.getName()));
        }
        // 2. Cached offline
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.getName() != null) {
            return CompletableFuture.completedFuture(new ResolvedPlayer(cached.getUniqueId(), cached.getName()));
        }
        // 3. Database lookup
        return plugin.getDatabaseManager().findUUIDByName(name).thenApply(uuid -> {
            if (uuid == null) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> sender.sendMessage(msg("player-not-found").replace("{player}", name)));
                return null;
            }
            return new ResolvedPlayer(uuid, name);
        });
    }

    // ── Message helpers ───────────────────────────────────────────────────────

    private String msg(String globalKey) {
        return MessageUtil.colorize(
                plugin.getConfigManager().getGlobalMessage(globalKey));
    }

    private String currencyMsg(String key, String player, double amount, double newBalance) {
        String raw = currency.getMessage(key);
        raw = raw.replace("{player}", player)
                 .replace("{amount}", currency.formatAmount(amount))
                 .replace("{new_balance}", currency.formatAmount(newBalance))
                 .replace("{default}", currency.formatAmount(currency.getDefaultBalance()))
                 .replace("{symbol}", currency.getSymbol())
                 .replace("{currency}", currency.getId())
                 .replace("{display-name}", currency.getDisplayName());
        return MessageUtil.colorize(raw);
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) completions.add(p.getName());
            }
            for (String action : ADMIN_ACTIONS) {
                if (action.startsWith(input) && sender.hasPermission("voideconomy." + currency.getId() + "." + action)) {
                    completions.add(action);
                }
            }
            return completions;
        }
        if (args.length == 2 && ADMIN_ACTIONS.contains(args[0].toLowerCase())) {
            String input = args[1].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) completions.add(p.getName());
            }
            return completions;
        }
        if (args.length == 3 && AMOUNT_ACTIONS.contains(args[0].toLowerCase())) {
            return List.of("1", "10", "100", "1000");
        }
        return List.of();
    }

    private record ResolvedPlayer(UUID uuid, String name) {}
}
