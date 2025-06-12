package net.mythofy.mythofySlashHub;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MythofySlashSend implements SimpleCommand {

    private final ProxyServer server;
    private final Logger logger;
    private final String sendMessage;
    private final String sentMessage;
    private final boolean requireConfirmationAll;
    private final boolean requireConfirmationCurrent;

    // Track pending confirmations: sender UUID -> PendingSend
    private final Map<UUID, PendingSend> pendingSends = new ConcurrentHashMap<>();

    private static class PendingSend {
        public final String type; // "*" or "*-"
        public final String server;
        public final Set<UUID> targets;
        public final long timestamp;

        public PendingSend(String type, String server, Set<UUID> targets) {
            this.type = type;
            this.server = server;
            this.targets = targets;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public MythofySlashSend(ProxyServer server, Logger logger, String sendMessage, String sentMessage,
                            boolean requireConfirmationAll, boolean requireConfirmationCurrent) {
        this.server = server;
        this.logger = logger;
        this.sendMessage = sendMessage;
        this.sentMessage = sentMessage;
        this.requireConfirmationAll = requireConfirmationAll;
        this.requireConfirmationCurrent = requireConfirmationCurrent;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        String label = invocation.alias(); // Get the command label used

        if (!(source instanceof Player)) {
            source.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        Player sender = (Player) source;

        // Handle /sendall <server> and /sendcurrent <server> as aliases
        if ((label.equalsIgnoreCase("sendall") || label.equalsIgnoreCase("sendcurrent")) && args.length == 1) {
            String destArg = args[0];
            if (label.equalsIgnoreCase("sendall")) {
                if (requireConfirmationAll) {
                    sendConfirmationMessage(sender, "*", destArg);
                    return;
                } else {
                    sendAll(sender, destArg);
                    return;
                }
            } else if (label.equalsIgnoreCase("sendcurrent")) {
                if (requireConfirmationCurrent) {
                    sendConfirmationMessage(sender, "*-", destArg);
                    return;
                } else {
                    sendCurrent(sender, destArg);
                    return;
                }
            }
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /send <player|*|*-|player,player,...> <server|player>", NamedTextColor.YELLOW));
            return;
        }

        String targetArg = args[0];
        String destArg = args[1];

        // Confirmation check
        if (pendingSends.containsKey(sender.getUniqueId())) {
            PendingSend pending = pendingSends.get(sender.getUniqueId());
            if (System.currentTimeMillis() - pending.timestamp > 10000) {
                pendingSends.remove(sender.getUniqueId());
                sender.sendMessage(Component.text("Confirmation expired. Please run the command again.", NamedTextColor.RED));
                return;
            }
            if (targetArg.equalsIgnoreCase("confirm")) {
                if (pending.type.equals("*")) {
                    sendAll(sender, pending.server);
                } else if (pending.type.equals("*-")) {
                    sendCurrent(sender, pending.server);
                }
                pendingSends.remove(sender.getUniqueId());
                return;
            }
        }

        if (targetArg.equals("*")) {
            // /send * <server>
            if (requireConfirmationAll) {
                sendConfirmationMessage(sender, "*", destArg);
                return;
            } else {
                sendAll(sender, destArg);
                return;
            }
        } else if (targetArg.equals("*-")) {
            // /send *- <server>
            if (requireConfirmationCurrent) {
                sendConfirmationMessage(sender, "*-", destArg);
                return;
            } else {
                sendCurrent(sender, destArg);
                return;
            }
        } else if (targetArg.contains(",")) {
            // /send player1,player2,... <server>
            String[] names = targetArg.split(",");
            sendPlayersToServer(sender, names, destArg);
            return;
        } else if (args.length == 2 && server.getPlayer(targetArg).isPresent() && server.getPlayer(destArg).isPresent()) {
            // /send <player> <player> (send player1 to player2's server)
            Player target = server.getPlayer(targetArg).get();
            Player destPlayer = server.getPlayer(destArg).get();
            Optional<RegisteredServer> destServer = destPlayer.getCurrentServer().map(s -> s.getServer());
            if (destServer.isPresent()) {
                sendPlayersToServer(sender, new String[]{target.getUsername()}, destServer.get().getServerInfo().getName());
            } else {
                sender.sendMessage(Component.text("Target player is not on a server.", NamedTextColor.RED));
            }
            return;
        } else {
            // /send <player> <server>
            sendPlayersToServer(sender, new String[]{targetArg}, destArg);
        }
    }

    private void sendConfirmationMessage(Player sender, String type, String server) {
        pendingSends.put(sender.getUniqueId(), new PendingSend(type, server, null));
        String msg = type.equals("*")
                ? "Are you sure you want to send ALL players to " + server + "?"
                : "Are you sure you want to send everyone on your server to " + server + "?";
        Component confirm = Component.text("[Click here to CONFIRM]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/send confirm"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to confirm this action!")));
        sender.sendMessage(Component.text(msg, NamedTextColor.RED).append(Component.space()).append(confirm));
    }

    private void sendAll(Player sender, String serverName) {
        Optional<RegisteredServer> regServer = server.getServer(serverName);
        if (regServer.isEmpty()) {
            sender.sendMessage(Component.text("Server not found: " + serverName, NamedTextColor.RED));
            return;
        }
        for (Player p : server.getAllPlayers()) {
            sendPlayer(sender, p, regServer.get());
        }
        sender.sendMessage(Component.text("All players have been sent to " + serverName + ".", NamedTextColor.GREEN));
    }

    private void sendCurrent(Player sender, String serverName) {
        Optional<RegisteredServer> regServer = server.getServer(serverName);
        if (regServer.isEmpty()) {
            sender.sendMessage(Component.text("Server not found: " + serverName, NamedTextColor.RED));
            return;
        }
        Optional<RegisteredServer> current = sender.getCurrentServer().map(s -> s.getServer());
        if (current.isEmpty()) {
            sender.sendMessage(Component.text("You are not on a server.", NamedTextColor.RED));
            return;
        }
        for (Player p : current.get().getPlayersConnected()) {
            sendPlayer(sender, p, regServer.get());
        }
        sender.sendMessage(Component.text("All players on your server have been sent to " + serverName + ".", NamedTextColor.GREEN));
    }

    private void sendPlayersToServer(Player sender, String[] names, String serverName) {
        Optional<RegisteredServer> regServer = server.getServer(serverName);
        if (regServer.isEmpty()) {
            sender.sendMessage(Component.text("Server not found: " + serverName, NamedTextColor.RED));
            return;
        }
        for (String name : names) {
            Optional<Player> target = server.getPlayer(name.trim());
            if (target.isPresent()) {
                sendPlayer(sender, target.get(), regServer.get());
            } else {
                sender.sendMessage(Component.text("Player not found: " + name, NamedTextColor.RED));
            }
        }
        sender.sendMessage(Component.text("Selected players have been sent to " + serverName + ".", NamedTextColor.GREEN));
    }

    private void sendPlayer(Player sender, Player target, RegisteredServer regServer) {
        if (target.getCurrentServer().isPresent() &&
            target.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase(regServer.getServerInfo().getName())) {
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("target", target.getUsername());
        placeholders.put("server", regServer.getServerInfo().getName());
        placeholders.put("sender", sender.getUsername());

        sender.sendMessage(formatMessage(sendMessage, placeholders));
        target.sendMessage(formatMessage(sentMessage, placeholders));
        target.createConnectionRequest(regServer).connect();
    }

    private Component formatMessage(String message, Map<String, String> placeholders) {
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return Component.text(result.replace("&", "§"));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 0) {
            suggestions.add("confirm");
            suggestions.add("*");
            suggestions.add("*-");
            for (Player p : server.getAllPlayers()) {
                suggestions.add(p.getUsername());
            }
            return suggestions;
        }

        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            if ("confirm".startsWith(arg)) suggestions.add("confirm");
            if ("*".startsWith(arg)) suggestions.add("*");
            if ("*-".startsWith(arg)) suggestions.add("*-");
            for (Player p : server.getAllPlayers()) {
                if (p.getUsername().toLowerCase().startsWith(arg)) {
                    suggestions.add(p.getUsername());
                }
            }
            return suggestions;
        }

        if (args.length == 2) {
            String arg = args[1].toLowerCase();
            for (RegisteredServer s : server.getAllServers()) {
                if (s.getServerInfo().getName().toLowerCase().startsWith(arg)) {
                    suggestions.add(s.getServerInfo().getName());
                }
            }
            for (Player p : server.getAllPlayers()) {
                if (p.getUsername().toLowerCase().startsWith(arg)) {
                    suggestions.add(p.getUsername());
                }
            }
            return suggestions;
        }

        return suggestions;
    }
}
