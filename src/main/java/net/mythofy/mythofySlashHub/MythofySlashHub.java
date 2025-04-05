package net.mythofy.mythofySlashHub;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Plugin(id = "mythofyslashhub", name = "MythofySlashHub", version = "1.0-SNAPSHOT", description = "My first plugin!!!!", authors = {"Kingdragoncat"})
public class MythofySlashHub {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    // Configuration values
    private String hubServerName;
    private boolean enableCooldown;
    private int cooldownSeconds;
    private String successMessage;
    private String errorMessage;

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
        // Load configuration
        loadConfig();

        // Register the /hub command
        server.getCommandManager().register("hub", new HubCommand());

        logger.info("MythofySlashHub plugin has been enabled!");
    }

    private void loadConfig() {
        // Create data directory if it doesn't exist
        if (!Files.exists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (IOException e) {
                logger.error("Failed to create plugin directory", e);
            }
        }

        // Create config file if it doesn't exist
        Path configPath = dataDirectory.resolve("config.toml");
        if (!Files.exists(configPath)) {
            try (InputStream in = getClass().getResourceAsStream("/config.toml")) {
                if (in != null) {
                    Files.copy(in, configPath);
                } else {
                    // Create default config manually
                    Files.writeString(configPath,
                            "# MythofySlashHub Configuration\n\n" +
                                    "# The name of your hub server as defined in velocity.toml\n" +
                                    "hub-server = \"hub\"\n\n" +
                                    "# Whether to enable a cooldown between uses of the command\n" +
                                    "enable-cooldown = false\n\n" +
                                    "# The cooldown period in seconds (only used if enable-cooldown is true)\n" +
                                    "cooldown-seconds = 5\n\n" +
                                    "# Message shown when connecting to hub (supports color codes with '&')\n" +
                                    "success-message = \"&aConnecting to the hub server...\"\n\n" +
                                    "# Message shown when hub server is unavailable (supports color codes with '&')\n" +
                                    "error-message = \"&cThe hub server is currently unavailable.\"\n");
                }
            } catch (IOException e) {
                logger.error("Failed to create default config", e);
            }
        }

        // Load the config values
        try {
            Toml config = new Toml().read(configPath.toFile());

            hubServerName = config.getString("hub-server", "hub");
            enableCooldown = config.getBoolean("enable-cooldown", false);
            cooldownSeconds = config.getLong("cooldown-seconds", 5L).intValue();
            successMessage = config.getString("success-message", "&aConnecting to the hub server...");
            errorMessage = config.getString("error-message", "&cThe hub server is currently unavailable.");

            logger.info("Configuration loaded with hub server: {}", hubServerName);
        } catch (Exception e) {
            logger.error("Failed to load configuration, using defaults", e);

            // Set defaults if config reading fails
            hubServerName = "hub";
            enableCooldown = false;
            cooldownSeconds = 5;
            successMessage = "&aConnecting to the hub server...";
            errorMessage = "&cThe hub server is currently unavailable.";
        }
    }

    // Utility method to translate color codes
    private Component formatMessage(String message) {
        return Component.text(message.replace("&", "§"));
    }

    private class HubCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();

            // Check if the command sender is a player
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

                // Update cooldown
                cooldowns.put(playerUuid, currentTime);

                // Clean up old cooldowns periodically
                if (Math.random() < 0.1) { // 10% chance to clean up on execution
                    cooldowns.entrySet().removeIf(entry ->
                            (currentTime - entry.getValue()) > (cooldownSeconds * 1000));
                }
            }

            // Try to find the hub server
            Optional<RegisteredServer> hubServer = server.getServer(hubServerName);

            if (hubServer.isEmpty()) {
                player.sendMessage(formatMessage(errorMessage));
                logger.warn("Hub server '{}' is not registered with Velocity", hubServerName);
                return;
            }

            // Connect to the hub server
            player.sendMessage(formatMessage(successMessage));
            player.createConnectionRequest(hubServer.get()).connect()
                    .thenAccept(result -> {
                        if (!result.isSuccessful()) {
                            player.sendMessage(Component.text("Failed to connect to the hub server: " +
                                            result.getReasonComponent().map(Component::examinableName).orElse("Unknown error"),
                                    NamedTextColor.RED));
                        }
                    });
        }
    }
}