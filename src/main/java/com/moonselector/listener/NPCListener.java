package com.moonselector.listener;

import com.moonselector.MoonSelectorPlugin;
import com.moonselector.manager.GUIManager;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Citizens2 NPC 우클릭 이벤트 처리
 * config.yml의 npc.ids 목록에 등록된 NPC에만 반응합니다.
 */
public class NPCListener implements Listener {

    private final MoonSelectorPlugin plugin;
    private final GUIManager guiManager;

    public NPCListener(MoonSelectorPlugin plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        Player player = event.getClicker();

        // 설정된 NPC ID 목록 확인
        List<Integer> npcIds = plugin.getConfig().getIntegerList("npc.ids");

        // NPC ID 목록이 비어있으면 모든 NPC에 반응 (편의상)
        // ID 목록이 있으면 해당 NPC만 반응
        if (!npcIds.isEmpty() && !npcIds.contains(npc.getId())) return;

        // 권한 확인
        if (!player.hasPermission("moonselector.use")) {
            player.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        // 이미 세션이 있으면 무시
        if (guiManager.hasSession(player.getUniqueId())) return;

        guiManager.openSelectorGUI(player);
    }
}
