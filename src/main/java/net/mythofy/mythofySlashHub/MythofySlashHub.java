package net.mythofy.mythofySlashHub;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MythofySlashHub {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    // Configuration values
    private Map<String, String> serverAliases = new HashMap<>(); // command -> actual server name
    private String defaultSuccessMessage;
    private String defaultErrorMessage;
    private String defaultAlreadyInServerMessage;
    private Map<String, String> perServerSuccessMessage = new HashMap<>();
    private Map<String, String> perServerErrorMessage = new HashMap<>();
    private Map<String, String> perServerAlreadyInMessage = new HashMap<>();

    private boolean enableCooldown;
    private int cooldownSeconds;

    // Cooldown tracking
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Inject
    public MythofySlashHub(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();

        // Register a command for each alias
        for (String command : serverAliases.keySet()) {
            server.getCommandManager().register(command, new ServerCommand(command));
        }

        logger.info("MythofySlashHub plugin has been enabled!");
    }

    private void loadConfig() {
        // Ensure the data directory exists
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create plugin data directory", e);
        }

        Path configPath = dataDirectory.resolve("config.toml");
        if (!Files.exists(configPath)) {
            try {
                // Create default config manually
                Files.writeString(configPath,
                        "# Configuration\n\n" +
                        "# The name of your hub server as defined in velocity.toml\n" +
                        "hub-server = \"hub\"\n" +
                        "minigames-server = \"minigames\"\n" +
                        "dev-server = \"dev\"\n\n" +
                        "# Whether to enable a cooldown between uses of the command\n" +
                        "enable-cooldown = false\n\n" +
                        "# The cooldown period in seconds (only used if enable-cooldown is true)\n" +
                        "cooldown-seconds = 5\n\n" +
                        "# Message shown when connecting to hub (supports color codes with '&')\n" +
                        "success-message = \"&aConnecting to the hub server...\"\n\n" +
                        "# Message shown when hub server is unavailable (supports color codes with '&')\n" +
                        "error-message = \"&cThe hub server is currently unavailable.\"\n\n" +
                        "# Message shown when player is already in the hub (supports color codes with '&')\n" +
                        "already-in-server-message = \"&eYou are already on this server!\"\n\n" +
                        "# Per-server messages (optional)\n" +
                        "minigames-success-message = \"&aConnecting to minigames!\"\n" +
                        "minigames-error-message = \"&cMinigames server is down.\"\n" +
                        "minigames-already-in-message = \"&eYou are already in minigames!\"\n\n" +
                        "dev-success-message = \"&aConnecting to the dev server!\"\n" +
                        "dev-error-message = \"&cDev server is currently unavailable.\"\n" +
                        "dev-already-in-message = \"&eYou are already in the dev server!\"\n"
                );
                logger.info("Default config.toml created at {}", configPath);
            } catch (IOException e) {
                logger.error("Failed to create default config", e);
            }
        }

        // Load the config values
        try {
            Toml config = new Toml().read(configPath.toFile());

            serverAliases.clear();
            perServerSuccessMessage.clear();
            perServerErrorMessage.clear();
            perServerAlreadyInMessage.clear();

            // Find all keys ending with -server
            for (Map.Entry<String, Object> entry : config.toMap().entrySet()) {
                String key = entry.getKey();
                if (key.endsWith("-server")) {
                    String command = key.substring(0, key.length() - "-server".length());
                    String serverName = String.valueOf(entry.getValue());
                    serverAliases.put(command, serverName);

                    // Per-server messages
                    String successKey = command + "-success-message";
                    String errorKey = command + "-error-message";
                    String alreadyInKey = command + "-already-in-message";
                    if (config.contains(successKey)) perServerSuccessMessage.put(command, config.getString(successKey));
                    if (config.contains(errorKey)) perServerErrorMessage.put(command, config.getString(errorKey));
                    if (config.contains(alreadyInKey)) perServerAlreadyInMessage.put(command, config.getString(alreadyInKey));
                }
            }

            enableCooldown = config.getBoolean("enable-cooldown", false);
            cooldownSeconds = config.getLong("cooldown-seconds", 5L).intValue();
            defaultSuccessMessage = config.getString("success-message", "&aConnecting to the hub server...");
            defaultErrorMessage = config.getString("error-message", "&cThe hub server is currently unavailable.");
            defaultAlreadyInServerMessage = config.getString("already-in-server-message", "&eYou are already on this server!");

            logger.info("Configuration loaded with server commands: {}", serverAliases);
        } catch (Exception e) {
            logger.error("Failed to load configuration, using defaults", e);

            // Set defaults if config reading fails
            serverAliases.clear();
            serverAliases.put("hub", "hub");
            enableCooldown = false;
            cooldownSeconds = 5;
            defaultSuccessMessage = "&aConnecting to the hub server...";
            defaultErrorMessage = "&cThe hub server is currently unavailable.";
            defaultAlreadyInServerMessage = "&eYou are already on this server!";
        }
    }

    // Utility method to translate color codes
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

            // Check cooldown if enabled
            if (enableCooldown) {
                long currentTime = System.currentTimeMillis();
                UUID playerUuid = player.getUniqueId();

                if (cooldowns.containsKey(playerUuid)) {
                    long lastUsage = cooldowns.get(playerUuid);
                    long remainingCooldown = (lastUsage + (cooldownSeconds * 1000)) - currentTime;

                    if (remainingCooldown > 0) {
                        int remainingSeconds = (int) Math.ceil(remainingCooldown / 1000.0);
                        player.sendMessage(Component.text("You must wait " + remainingSeconds + " second(s) before using this command again.", NamedTextColor.RED));
                        return;
                    }
                }

                cooldowns.put(playerUuid, currentTime);

                if (Math.random() < 0.1) {
                    cooldowns.entrySet().removeIf(entry ->
                            (currentTime - entry.getValue()) > (cooldownSeconds * 1000));
                }
            }

            String targetServer = serverAliases.get(command);
            if (targetServer == null) {
                player.sendMessage(Component.text("Server alias not found in config.", NamedTextColor.RED));
                return;
            }

            Optional<RegisteredServer> regServer = server.getServer(targetServer);

            String alreadyInMsg = perServerAlreadyInMessage.getOrDefault(command, defaultAlreadyInServerMessage);
            String successMsg = perServerSuccessMessage.getOrDefault(command, defaultSuccessMessage);
            String errorMsg = perServerErrorMessage.getOrDefault(command, defaultErrorMessage);

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
