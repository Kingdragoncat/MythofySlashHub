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
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.mythofy.mythofySlashHub.send.SlashSendEntrypoint;

public class MythofySlashHub {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private configmanager configManager;
    private SlashHubLogic slashHubLogic;
    private SlashSendEntrypoint slashSendEntrypoint;

    @Inject
    public MythofySlashHub(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();

        // Prevent reserved alias "reload"
        if (configManager.serverAliases.containsKey("reload")) {
            logger.error("The alias 'reload' is reserved and cannot be used as a server alias.");
            configManager.serverAliases.remove("reload");
        }

        // Initialize logic handler
        slashHubLogic = new SlashHubLogic(server, logger, configManager);

        // Register commands using logic handler
        for (String command : configManager.serverAliases.keySet()) {
            server.getCommandManager().register(command, slashHubLogic.createServerCommand(command));
        }

        // Register /reload command
        server.getCommandManager().register("reload", new reload(this));

        // Register /send command via SlashSendEntrypoint
        slashSendEntrypoint = new SlashSendEntrypoint(server, logger, dataDirectory);
        slashSendEntrypoint.onProxyInitialization(event);

        logger.info("MythofySlashHub plugin has been enabled!");
    }

    public void reloadConfig() {
        loadConfig();
        // Re-register server commands after reload
        for (String command : configManager.serverAliases.keySet()) {
            server.getCommandManager().register(command, slashHubLogic.createServerCommand(command));
        }
        logger.info("MythofySlashHub configuration reloaded.");
    }

    private void loadConfig() {
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
                Files.writeString(configPath,
                        "# MythofySlashHub Configuration\n\n" +
                        "# === Server Aliases ===\n" +
                        "hub-server = \"hub\"\n" +
                        "minigames-server = \"minigames\"\n" +
                        "dev-server = \"dev\"\n" +
                        "build-server = \"build\"\n" +
                        "lifesteal-server = \"lifesteal\"\n" +
                        "box-server = \"box\"\n\n" +
                        "# === Cooldown Settings ===\n" +
                        "enable-cooldown = false\n" +
                        "cooldown-seconds = 5\n\n" +
                        "# === Default Messages ===\n" +
                        "success-message = \"&aConnecting to the hub server...\"\n" +
                        "error-message = \"&cThe hub server is currently unavailable.\"\n" +
                        "already-in-server-message = \"&eYou are already on this server!\"\n\n" +
                        "# === Per-Server Messages (Optional) ===\n" +
                        "minigames-success-message = \"&aConnecting to minigames!\"\n" +
                        "minigames-error-message = \"&cMinigames server is down.\"\n" +
                        "minigames-already-in-message = \"&eYou are already in minigames!\"\n\n" +
                        "dev-success-message = \"&aConnecting to the dev server!\"\n" +
                        "dev-error-message = \"&cDev server is currently unavailable.\"\n" +
                        "dev-already-in-message = \"&eYou are already in the dev server!\"\n\n" +
                        "build-success-message = \"&aConnecting to the build server!\"\n" +
                        "build-error-message = \"&cBuild server is currently unavailable.\"\n" +
                        "build-already-in-message = \"&eYou are already in the build server!\"\n\n" +
                        "lifesteal-success-message = \"&aConnecting to the lifesteal server!\"\n" +
                        "lifesteal-error-message = \"&cLifesteal server is currently unavailable.\"\n" +
                        "lifesteal-already-in-message = \"&eYou are already in the lifesteal server!\"\n\n" +
                        "box-success-message = \"&aConnecting to the box server!\"\n" +
                        "box-error-message = \"&cBox server is currently unavailable.\"\n" +
                        "box-already-in-message = \"&eYou are already in the box server!\"\n"
                );
                logger.info("Default config.toml created at {}", configPath);
            } catch (IOException e) {
                logger.error("Failed to create default config", e);
            }
        }

        configManager = new configmanager(configPath, logger);

        // Create permissionlist file with dynamic server permissions
        Path permissionListPath = dataDirectory.resolve("permissionlist");
        if (!Files.exists(permissionListPath)) {
            try {
                StringBuilder perms = new StringBuilder();
                perms.append("# MythofySlashHub Permissions Guide\n\n");
                perms.append("# Per-command permissions (replace <command> with the alias, e.g., hub, minigames, dev, etc):\n");
                perms.append("MythofySlashHub.command.<command>\n\n");
                perms.append("# Send commands:\n");
                perms.append("MythofySlashHub.sendall    # Allows use of /send all <server> and /send <fromServer> <toServer>\n");
                perms.append("MythofySlashHub.send       # Allows use of /send <player> <server> and /send [<player1> ...] <server>\n\n");
                perms.append("# Per-server go permissions (auto-generated):\n");

                // Track reserved permission names to avoid conflicts
                Set<String> reserved = Set.of("sendall", "send");

                Map<String, Integer> nameCounts = new HashMap<>();

                for (String alias : configManager.serverAliases.keySet()) {
                    String serverName = configManager.serverAliases.get(alias);
                    if (serverName != null && !serverName.isEmpty()) {
                        String base = serverName.substring(0, 1).toUpperCase() + serverName.substring(1);
                        String permBase = base;
                        String perm = "MythofySlashHub.Go" + permBase;

                        // If the server name matches a reserved permission, add a number suffix
                        if (reserved.contains(serverName.toLowerCase())) {
                            int count = nameCounts.getOrDefault(serverName.toLowerCase(), 1);
                            perm += count;
                            nameCounts.put(serverName.toLowerCase(), count + 1);
                        }

                        perms.append(perm)
                             .append("    # Allows use of /").append(alias).append(" to go to ").append(serverName).append("\n");
                    }
                }
                perms.append("\n# Example:\n");
                perms.append("MythofySlashHub.command.hub\n");
                perms.append("MythofySlashHub.command.minigames\n");
                perms.append("MythofySlashHub.sendall\n");
                perms.append("MythofySlashHub.send\n");
                for (String alias : configManager.serverAliases.keySet()) {
                    String serverName = configManager.serverAliases.get(alias);
                    if (serverName != null && !serverName.isEmpty()) {
                        String base = serverName.substring(0, 1).toUpperCase() + serverName.substring(1);
                        String permBase = base;
                        String perm = "MythofySlashHub.Go" + permBase;
                        if (reserved.contains(serverName.toLowerCase())) {
                            int count = nameCounts.getOrDefault(serverName.toLowerCase(), 1);
                            perm += count;
                            nameCounts.put(serverName.toLowerCase(), count + 1);
                        }
                        perms.append(perm).append("\n");
                    }
                }
                Files.writeString(permissionListPath, perms.toString());
                logger.info("Permission list created at {}", permissionListPath);
            } catch (IOException e) {
                logger.error("Failed to create permissionlist file", e);
            }
        }
    }
}