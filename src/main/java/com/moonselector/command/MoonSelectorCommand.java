package com.moonselector.command;

import com.moonselector.MoonSelectorPlugin;
import com.moonselector.listener.TriggerItemListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * /아이템선택 명령어 처리
 * 권한: moonselector.admin (op 기본값)
 */
public class MoonSelectorCommand implements CommandExecutor, TabCompleter {

    private final MoonSelectorPlugin plugin;

    public MoonSelectorCommand(MoonSelectorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 콘솔 차단
        if (!(sender instanceof Player player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        // 관리자 권한 확인
        if (!player.hasPermission("moonselector.admin")) {
            MoonSelectorPlugin.sendMessage(player, plugin.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "아이템설정" -> handleItemSetup(player);
            case "트리거아이템" -> handleTriggerItem(player);
            case "npc등록" -> handleNpcRegister(player, args);
            case "npc해제" -> handleNpcUnregister(player, args);
            case "reload" -> handleReload(player);
            default -> sendHelp(player);
        }

        return true;
    }

    /**
     * /아이템선택 아이템설정 -> 관리자 GUI 열기
     */
    private void handleItemSetup(Player player) {
        plugin.getGUIManager().openAdminSetupGUI(player);
    }

    /**
     * /아이템선택 트리거아이템 -> 손에 든 아이템을 트리거 아이템으로 변환
     */
    private void handleTriggerItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&c손에 아이템을 들고 사용하세요!");
            return;
        }

        // 트리거 태그 부여
        ItemStack tagged = TriggerItemListener.makeTriggerItem(held);
        player.getInventory().setItemInMainHand(tagged);
        MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&a손에 든 아이템이 트리거 아이템으로 설정되었습니다!");
    }

    /**
     * /아이템선택 npc등록 <NPC ID> -> NPC ID를 config에 등록
     */
    private void handleNpcRegister(Player player, String[] args) {
        if (args.length < 2) {
            MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&e사용법: /아이템선택 npc등록 <NPC ID>");
            return;
        }

        int npcId;
        try {
            npcId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&cNPC ID는 숫자여야 합니다!");
            return;
        }

        List<Integer> ids = plugin.getConfig().getIntegerList("npc.ids");
        if (ids.contains(npcId)) {
            MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&eNPC ID " + npcId + "는 이미 등록되어 있습니다.");
            return;
        }

        ids.add(npcId);
        plugin.getConfig().set("npc.ids", ids);
        plugin.saveConfig();

        MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&aNPC ID " + npcId + " 등록 완료!");
    }

    /**
     * /아이템선택 npc해제 <NPC ID> -> NPC ID를 config에서 제거
     */
    private void handleNpcUnregister(Player player, String[] args) {
        if (args.length < 2) {
            MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&e사용법: /아이템선택 npc해제 <NPC ID>");
            return;
        }

        int npcId;
        try {
            npcId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&cNPC ID는 숫자여야 합니다!");
            return;
        }

        List<Integer> ids = plugin.getConfig().getIntegerList("npc.ids");
        if (!ids.remove((Integer) npcId)) {
            MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&cNPC ID " + npcId + "는 등록되어 있지 않습니다.");
            return;
        }

        plugin.getConfig().set("npc.ids", ids);
        plugin.saveConfig();

        MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&aNPC ID " + npcId + " 해제 완료!");
    }

    /**
     * /아이템선택 reload -> config 및 아이템 목록 리로드
     */
    private void handleReload(Player player) {
        plugin.reloadConfig();
        plugin.getItemManager().loadItems();
        MoonSelectorPlugin.sendMessage(player, plugin.getPrefix() + "&a설정 파일 리로드 완료!");
    }

    private void sendHelp(Player player) {
        MoonSelectorPlugin.sendMessage(player, "&8&m──────────────────────────────");
        MoonSelectorPlugin.sendMessage(player, "&6&l아이템선택 &7관리자 명령어");
        MoonSelectorPlugin.sendMessage(player, "&e/아이템선택 아이템설정 &7- 아이템 GUI 설정");
        MoonSelectorPlugin.sendMessage(player, "&e/아이템선택 트리거아이템 &7- 손에 든 아이템을 트리거로 설정");
        MoonSelectorPlugin.sendMessage(player, "&e/아이템선택 npc등록 <ID> &7- NPC 등록");
        MoonSelectorPlugin.sendMessage(player, "&e/아이템선택 npc해제 <ID> &7- NPC 해제");
        MoonSelectorPlugin.sendMessage(player, "&e/아이템선택 reload &7- 설정 리로드");
        MoonSelectorPlugin.sendMessage(player, "&8&m──────────────────────────────");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("moonselector.admin")) return List.of();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(List.of(
                "아이템설정", "트리거아이템", "npc등록", "npc해제", "reload"
            ));
            completions.removeIf(s -> !s.startsWith(args[0]));
            return completions;
        }

        return List.of();
    }
}
