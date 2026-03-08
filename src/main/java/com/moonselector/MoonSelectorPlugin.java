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

    // 헥스 컬러 패턴: #RRGGBB
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

    // ─────────────────────────────────────────────────────────────
    // 메시지 전송 (헥스 컬러 지원)
    // ─────────────────────────────────────────────────────────────

    /**
     * 플레이어에게 헥스 컬러 지원 메시지 전송
     * #RRGGBB 형식과 &코드 모두 지원
     */
    public static void sendMessage(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        player.sendMessage(parseColor(message));
    }

    /**
     * config.yml messages 섹션에서 메시지를 읽어 전송
     */
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

    /**
     * #RRGGBB 헥스 코드와 &색상 코드를 Adventure Component로 변환
     *
     * 처리 순서:
     *  1. #RRGGBB → §x§R§R§G§G§B§B (legacy hex 형식)
     *  2. & → § (legacy color code)
     *  3. LegacyComponentSerializer로 Component 생성
     */
    public static Component parseColor(String text) {
        if (text == null) return Component.empty();

        // 1. #RRGGBB 헥스 → §x§r§r§g§g§b§b 형식으로 변환
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

        // 2. & → § 변환
        String legacyText = sb.toString().replace('&', '§');

        // 3. Adventure LegacyComponentSerializer로 파싱 (헥스 지원)
        return LegacyComponentSerializer.legacySection().deserialize(legacyText);
    }

    /**
     * 하위 호환용 - GUI 타이틀/lore 등 문자열이 필요한 곳에서 사용
     * (Player에게 직접 보내지 않고 문자열로 필요한 경우)
     */
    public static String colorize(String text) {
        if (text == null) return "";
        // 헥스 변환
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

    // 기존 코드 호환용 (GUIManager 등에서 사용 중)
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
}
