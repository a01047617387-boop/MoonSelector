package com.moonselector;

import com.moonselector.command.MoonSelectorCommand;
import com.moonselector.listener.GUIListener;
import com.moonselector.listener.NPCListener;
import com.moonselector.listener.TriggerItemListener;
import com.moonselector.manager.ItemManager;
import com.moonselector.manager.GUIManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MoonSelectorPlugin extends JavaPlugin {

    private static MoonSelectorPlugin instance;
    private ItemManager itemManager;
    private GUIManager guiManager;
    private boolean citizensEnabled = false;

    private static final Pattern HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.itemManager = new ItemManager(this);
        this.guiManager = new GUIManager(this);
        itemManager.loadItems();

        MoonSelectorCommand command = new MoonSelectorCommand(this);
        var cmd = getCommand("아이템선택");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new TriggerItemListener(this), this);

        if (getServer().getPluginManager().getPlugin("Citizens") != null) {
            citizensEnabled = true;
            getServer().getPluginManager().registerEvents(new NPCListener(this), this);
            getLogger().info("Citizens 연동 활성화!");
        } else {
            getLogger().info("Citizens 플러그인 없음 - NPC 연동 비활성화");
        }

        getLogger().info("MoonSelector v" + getDescription().getVersion() + " 활성화 완료!");
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.closeAllSessions();
        }
        getLogger().info("MoonSelector 비활성화 완료!");
    }

    public static MoonSelectorPlugin getInstance() {
        return instance;
    }

    public ItemManager getItemManager() { return itemManager; }
    public GUIManager getGUIManager() { return guiManager; }
    public boolean isCitizensEnabled() { return citizensEnabled; }

    public static void sendMessage(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        player.sendMessage(parseColor(message));
    }

    public void sendConfigMessage(Player player, String key) {
        String raw = getConfig().getString("messages." + key, "");
        sendMessage(player, raw);
    }

    public void sendConfigMessage(Player player, String key, String... replacements) {
        String raw = getConfig().getString("messages." + key, "");
        for (int i = 0; i < replacements.length - 1; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        sendMessage(player, raw);
    }

    public static Component parseColor(String text) {
        if (text == null) return Component.empty();

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder legacyHex = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                legacyHex.append('§').append(c);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(legacyHex.toString()));
        }
        matcher.appendTail(sb);

        String legacyText = sb.toString().replace('&', '§');
        return LegacyComponentSerializer.legacySection().deserialize(legacyText);
    }

    public static String colorize(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder legacyHex = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                legacyHex.append('§').append(c);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(legacyHex.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString().replace('&', '§');
    }

    public String getMessage(String key) {
        return getConfig().getString("messages." + key, "");
    }

    public String getMessage(String key, String... replacements) {
        String msg = getMessage(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    public String getPrefix() {
        return getConfig().getString("messages.prefix", "&7[&bMoonSelector&7] ");
    }
}