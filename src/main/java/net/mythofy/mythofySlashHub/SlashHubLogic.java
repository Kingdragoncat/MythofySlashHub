package net.mythofy.mythofySlashHub;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.*;

public class SlashHubLogic {

    private final ProxyServer server;
    private final Logger logger;
    private final configmanager configManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public SlashHubLogic(ProxyServer server, Logger logger, configmanager configManager) {
        this.server = server;
        this.logger = logger;
        this.configManager = configManager;
    }

    public SimpleCommand createServerCommand(String command) {
        return new ServerCommand(command);
    }

    private Component formatMessage(String message) {
        return Component.text(message.replace("&", "§"));
    }

    private class ServerCommand implements SimpleCommand {
        private final String command;

        public ServerCommand(String command) {
            this.command = command;
        }

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();

            if (!(source instanceof Player)) {
                source.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                return;
            }

            Player player = (Player) source;

            // Permission check: MythofySlashHub.Go<ServerName> (with suffix if reserved)
            String targetServer = configManager.serverAliases.get(command);
            if (targetServer == null) {
                player.sendMessage(Component.text("Server alias not found in config.", NamedTextColor.RED));
                return;
            }
            String capitalized = targetServer.substring(0, 1).toUpperCase() + targetServer.substring(1);
            String permission = "MythofySlashHub.Go" + capitalized;

            // Reserved permission names
            Set<String> reserved = new HashSet<>(Arrays.asList("sendall", "send"));
            if (reserved.contains(targetServer.toLowerCase())) {
                // Always use "1" as suffix for the first conflict
                permission += "1";
            }

            if (!player.hasPermission(permission)) {
                player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }

            if (configManager.enableCooldown) {
                long currentTime = System.currentTimeMillis();
                UUID playerUuid = player.getUniqueId();

                if (cooldowns.containsKey(playerUuid)) {
                    long lastUsage = cooldowns.get(playerUuid);
                    long remainingCooldown = (lastUsage + (configManager.cooldownSeconds * 1000)) - currentTime;

                    if (remainingCooldown > 0) {
                        int remainingSeconds = (int) Math.ceil(remainingCooldown / 1000.0);
                        player.sendMessage(Component.text("You must wait " + remainingSeconds + " second(s) before using this command again.", NamedTextColor.RED));
                        return;
                    }
                }

                cooldowns.put(playerUuid, currentTime);

                if (Math.random() < 0.1) {
                    cooldowns.entrySet().removeIf(entry ->
                            (currentTime - entry.getValue()) > (configManager.cooldownSeconds * 1000));
                }
            }

            Optional<RegisteredServer> regServer = server.getServer(targetServer);

            String alreadyInMsg = configManager.perServerAlreadyInMessage.getOrDefault(command, configManager.defaultAlreadyInServerMessage);
            String successMsg = configManager.perServerSuccessMessage.getOrDefault(command, configManager.defaultSuccessMessage);
            String errorMsg = configManager.perServerErrorMessage.getOrDefault(command, configManager.defaultErrorMessage);

            if (regServer.isEmpty()) {
                player.sendMessage(formatMessage(errorMsg));
                logger.warn("Server '{}' (command '{}') is not registered with Velocity", targetServer, command);
                return;
            }

            if (player.getCurrentServer().isPresent() &&
                player.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase(targetServer)) {
                player.sendMessage(formatMessage(alreadyInMsg));
                return;
            }

            player.sendMessage(formatMessage(successMsg));
            player.createConnectionRequest(regServer.get()).connect()
                    .thenAccept(result -> {
                        if (!result.isSuccessful()) {
                            player.sendMessage(Component.text("Failed to connect to the server: " +
                                            result.getReasonComponent().map(Component::examinableName).orElse("Unknown error"),
                                    NamedTextColor.RED));
                        }
                    });
        }
    }
}