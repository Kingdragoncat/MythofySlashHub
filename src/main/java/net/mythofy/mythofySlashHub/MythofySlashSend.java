package net.mythofy.mythofySlashHub;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.*;

public class MythofySlashSend implements SimpleCommand {
    private final ProxyServer server;
    private final Logger logger;
    private final Map<UUID, PendingSend> pendingSends = new HashMap<>();

    // These should be loaded from config if you want them configurable
    private final String sendMessage = "&aYou have sent {target} to {server}!";
    private final String sentMessage = "&eYou have been sent to {server} by {sender}!";

    // Confirmation settings (should be loaded from config)
    private final boolean requireConfirmationAll = true;
    private final boolean requireConfirmationCurrent = true;
    private final boolean requireConfirmationMulti = false;

    private static class PendingSend {
        public final List<Player> targets;
        public final String serverName;
        public final boolean force;
        public final long timestamp;

        public PendingSend(List<Player> targets, String serverName, boolean force) {
            this.targets = targets;
            this.serverName = serverName;
            this.force = force;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public MythofySlashSend(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        // Confirmation logic is here:
        // - /send confirm and /send deny handled for pending sends
        // - Confirmation required for /send all, /send +<server>, and (optionally) multi-player sends

        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            sendHelp(source);
            return;
        }

        if (args.length == 1 && (args[0].equalsIgnoreCase("confirm") || args[0].equalsIgnoreCase("deny"))) {
            if (!(source instanceof Player)) {
                source.sendMessage(Component.text("Only players can confirm or deny send actions.", NamedTextColor.RED));
                return;
            }
            Player sender = (Player) source;
            UUID uuid = sender.getUniqueId();
            PendingSend pending = pendingSends.get(uuid);
            if (pending == null) {
                sender.sendMessage(Component.text("You have no pending send action to confirm or deny.", NamedTextColor.RED));
                return;
            }
            if (args[0].equalsIgnoreCase("confirm")) {
                if (System.currentTimeMillis() - pending.timestamp > 10000) {
                    pendingSends.remove(uuid);
                    sender.sendMessage(Component.text("Confirmation expired. Please run the command again.", NamedTextColor.RED));
                    return;
                }
                performSend(sender, pending.targets, pending.serverName, pending.force);
                pendingSends.remove(uuid);
            } else {
                pendingSends.remove(uuid);
                sender.sendMessage(Component.text("Send action cancelled.", NamedTextColor.YELLOW));
            }
            return;
        }

        if (!(source instanceof Player)) {
            source.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        Player sender = (Player) source;
        UUID uuid = sender.getUniqueId();

        PendingSend pending = pendingSends.get(uuid);
        if (pending != null) {
            sender.sendMessage(
                Component.text("You have a pending send confirmation. Use ")
                    .append(Component.text("/send confirm", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/send confirm")))
                    .append(Component.text(" or "))
                    .append(Component.text("/send deny", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/send deny")))
                    .append(Component.text("."))
            );
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Type /send help for usage instructions.", NamedTextColor.YELLOW));
            return;
        }

        boolean force = false;
        int argCount = args.length;
        String lastArg = args[argCount - 1].toLowerCase();
        if ((lastArg.equals("-f") || lastArg.equals("force"))) {
            if (!sender.hasPermission("slashhub.send.bypass.override")) {
                sender.sendMessage(Component.text("You do not have permission to force bypass.", NamedTextColor.RED));
                return;
            }
            force = true;
            argCount--;
        }

        String firstArg = args[0];
        String destServerName = args[argCount - 1];
        List<Player> targets = new ArrayList<>();
        boolean needsConfirmation = false;

        if (firstArg.equalsIgnoreCase("all")) {
            if (!sender.hasPermission("slashhub.send.all")) {
                sender.sendMessage(Component.text("You do not have permission to send all players.", NamedTextColor.RED));
                return;
            }
            for (Player p : server.getAllPlayers()) {
                if (!p.getUniqueId().equals(sender.getUniqueId())) {
                    if (!force && p.hasPermission("slashhub.send.bypass")) continue;
                    targets.add(p);
                }
            }
            needsConfirmation = requireConfirmationAll;
        } else if (firstArg.startsWith("+")) {
            if (!sender.hasPermission("slashhub.send.server")) {
                sender.sendMessage(Component.text("You do not have permission to send all players from a server.", NamedTextColor.RED));
                return;
            }
            String fromServerName = firstArg.substring(1);
            Optional<RegisteredServer> fromServer = server.getServer(fromServerName);
            if (fromServer.isEmpty()) {
                sender.sendMessage(Component.text("Source server not found: " + fromServerName, NamedTextColor.RED));
                return;
            }
            for (Player p : server.getAllPlayers()) {
                if (p.getCurrentServer().isPresent() &&
                    p.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase(fromServerName) &&
                    !p.getUniqueId().equals(sender.getUniqueId())) {
                    if (!force && p.hasPermission("slashhub.send.bypass")) continue;
                    targets.add(p);
                }
            }
            needsConfirmation = requireConfirmationCurrent;
        } else {
            if (!sender.hasPermission("slashhub.send")) {
                sender.sendMessage(Component.text("You do not have permission to send players.", NamedTextColor.RED));
                return;
            }
            Set<String> playerNames = new LinkedHashSet<>();
            for (String name : firstArg.split(",")) {
                String n = name.trim();
                if (!n.isEmpty()) playerNames.add(n);
            }
            for (String name : playerNames) {
                Optional<Player> target = server.getPlayer(name);
                if (target.isPresent()) {
                    Player t = target.get();
                    if (!t.getUniqueId().equals(sender.getUniqueId())) {
                        if (!force && t.hasPermission("slashhub.send.bypass")) continue;
                        targets.add(t);
                    }
                } else {
                    sender.sendMessage(Component.text("Player not found: " + name, NamedTextColor.RED));
                }
            }
            // Confirmation for multi-player send if enabled
            if (targets.size() > 1 && requireConfirmationMulti) {
                needsConfirmation = true;
            }
            if (targets.isEmpty()) {
                sender.sendMessage(Component.text("No valid players to send.", NamedTextColor.RED));
                return;
            }
            if (!needsConfirmation) {
                performSend(sender, targets, destServerName, force);
                return;
            }
        }

        // Confirmation for all, +<server>, and multi if enabled
        if (needsConfirmation) {
            pendingSends.put(uuid, new PendingSend(targets, destServerName, force));
            String confirmMsg;
            if (firstArg.equalsIgnoreCase("all")) {
                confirmMsg = "Are you sure you want to send ALL players to " + destServerName + (force ? " (forced)" : "") + "? ";
            } else if (firstArg.startsWith("+")) {
                confirmMsg = "Are you sure you want to send all players from " + firstArg.substring(1) + " to " + destServerName + (force ? " (forced)" : "") + "? ";
            } else {
                confirmMsg = "Are you sure you want to send " + targets.size() + " players to " + destServerName + (force ? " (forced)" : "") + "? ";
            }
            sender.sendMessage(
                Component.text(confirmMsg, NamedTextColor.RED)
                    .append(Component.text("[CONFIRM]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/send confirm")))
                    .append(Component.text(" "))
                    .append(Component.text("[DENY]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/send deny")))
            );
            return;
        }
    }

    private void performSend(Player sender, List<Player> targets, String serverName, boolean force) {
        Optional<RegisteredServer> regServer = server.getServer(serverName);
        if (regServer.isEmpty()) {
            sender.sendMessage(Component.text("Server not found: " + serverName, NamedTextColor.RED));
            return;
        }
        for (Player t : targets) {
            if (t.getCurrentServer().isPresent() &&
                t.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase(serverName)) {
                continue;
            }
            if (!force && t.hasPermission("slashhub.send.bypass")) continue;
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("target", t.getUsername());
            placeholders.put("server", regServer.get().getServerInfo().getName());
            placeholders.put("sender", sender.getUsername());

            sender.sendMessage(formatMessage(sendMessage, placeholders));
            t.sendMessage(formatMessage(sentMessage, placeholders));
            t.createConnectionRequest(regServer.get()).connect();
        }
        sender.sendMessage(Component.text("Selected players have been sent to " + serverName + (force ? " (forced)" : "") + ".", NamedTextColor.GREEN));
    }

    private Component formatMessage(String message, Map<String, String> placeholders) {
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return Component.text(result.replace("&", "§"));
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(Component.text("§e--- /send Command Help ---"));
        source.sendMessage(Component.text("§7/send <player> <server> §f- Send a player to a server"));
        source.sendMessage(Component.text("§7/send <player1>,<player2> <server> §f- Send multiple players to a server"));
        source.sendMessage(Component.text("§7/send +<server> <server> §f- Send all players from a server to another server §c[confirmation required]"));
        source.sendMessage(Component.text("§7/send all <server> §f- Send all players on the network to a server §c[confirmation required]"));
        source.sendMessage(Component.text("§7/send <...> <...> -f §f- Force send (override bypass, requires permission)"));
        source.sendMessage(Component.text("§7/send confirm §f- Confirm a pending mass send"));
        source.sendMessage(Component.text("§7/send deny §f- Deny/cancel a pending mass send"));
        source.sendMessage(Component.text("§7Tab completion is available for player names, server names, and flags."));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();
        CommandSource source = invocation.source();

        if (args.length == 0) {
            suggestions.add("help");
            suggestions.add("all");
            for (RegisteredServer s : server.getAllServers()) suggestions.add("+" + s.getServerInfo().getName());
            for (Player p : server.getAllPlayers()) suggestions.add(p.getUsername());
            return suggestions;
        }

        if (args.length == 1) {
            String lastArg = args[0].toLowerCase();
            if ("help".startsWith(lastArg)) suggestions.add("help");
            if ("all".startsWith(lastArg)) suggestions.add("all");
            for (RegisteredServer s : server.getAllServers())
                if (("+" + s.getServerInfo().getName()).toLowerCase().startsWith(lastArg))
                    suggestions.add("+" + s.getServerInfo().getName());
            String[] split = lastArg.split(",");
            String last = split[split.length - 1];
            for (Player p : server.getAllPlayers())
                if (p.getUsername().toLowerCase().startsWith(last))
                    suggestions.add((lastArg.endsWith(",") ? lastArg : lastArg.substring(0, lastArg.lastIndexOf(",") + 1)) + p.getUsername());
            return suggestions;
        }

        if (args.length == 2) {
            String lastArg = args[1].toLowerCase();
            for (RegisteredServer s : server.getAllServers())
                if (s.getServerInfo().getName().toLowerCase().startsWith(lastArg))
                    suggestions.add(s.getServerInfo().getName());
            if (source instanceof Player && ((Player) source).hasPermission("slashhub.send.bypass.override")) {
                if ("-f".startsWith(lastArg)) suggestions.add("-f");
                if ("force".startsWith(lastArg)) suggestions.add("force");
            }
            return suggestions;
        }

        if (args.length == 3) {
            if (source instanceof Player && ((Player) source).hasPermission("slashhub.send.bypass.override")) {
                String lastArg = args[2].toLowerCase();
                if ("-f".startsWith(lastArg)) suggestions.add("-f");
                if ("force".startsWith(lastArg)) suggestions.add("force");
            }
        }

        return suggestions;
    }
}
