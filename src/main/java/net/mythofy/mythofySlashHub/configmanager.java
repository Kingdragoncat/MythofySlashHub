package net.mythofy.mythofySlashHub;

import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class configmanager {
    private final Path configPath;
    private final Logger logger;

    public Map<String, String> serverAliases = new HashMap<>();
    public String defaultSuccessMessage;
    public String defaultErrorMessage;
    public String defaultAlreadyInServerMessage;
    public Map<String, String> perServerSuccessMessage = new HashMap<>();
    public Map<String, String> perServerErrorMessage = new HashMap<>();
    public Map<String, String> perServerAlreadyInMessage = new HashMap<>();
    public boolean enableCooldown;
    public int cooldownSeconds;

    // Compass config (GUI removed)
    public boolean compassEnabled = true;
    public int compassSlot = 4;
    public String compassMaterial = "COMPASS";
    public String compassName = "&bServer Navigator";
    public String compassLore = "&7Click to open the server menu!";

    public configmanager(Path configPath, Logger logger) {
        this.configPath = configPath;
        this.logger = logger;
        loadConfig();
    }

    public void loadConfig() {
        try {
            Toml config = new Toml().read(configPath.toFile());

            serverAliases.clear();
            perServerSuccessMessage.clear();
            perServerErrorMessage.clear();
            perServerAlreadyInMessage.clear();

            for (Map.Entry<String, Object> entry : config.toMap().entrySet()) {
                String key = entry.getKey();
                if (key.endsWith("-server")) {
                    String command = key.substring(0, key.length() - "-server".length());
                    String serverName = String.valueOf(entry.getValue());
                    serverAliases.put(command, serverName);

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

            // Compass config
            Toml compass = config.getTable("compass");
            if (compass != null) {
                compassEnabled = compass.getBoolean("enabled", true);
                compassSlot = compass.getLong("slot", 4L).intValue();
                compassMaterial = compass.getString("material", "COMPASS");
                compassName = compass.getString("name", "&bServer Navigator");
                compassLore = compass.getString("lore", "&7Click to open the server menu!");
            }
            // GUI config removed

        } catch (Exception e) {
            logger.error("Failed to load configuration, using defaults", e);
            serverAliases.clear();
            serverAliases.put("hub", "hub");
            serverAliases.put("minigames", "minigames");
            serverAliases.put("dev", "dev");
            serverAliases.put("build", "build");
            serverAliases.put("lifesteal", "lifesteal");
            serverAliases.put("box", "box");
            enableCooldown = false;
            cooldownSeconds = 5;
            defaultSuccessMessage = "&aConnecting to the hub server...";
            defaultErrorMessage = "&cThe hub server is currently unavailable.";
            defaultAlreadyInServerMessage = "&eYou are already on this server!";
        }
    }
}
