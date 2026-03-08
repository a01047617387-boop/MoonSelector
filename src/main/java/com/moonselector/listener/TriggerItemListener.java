package com.moonselector.listener;

import com.moonselector.MoonSelectorPlugin;
import com.moonselector.manager.GUIManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 트리거 아이템 우클릭 -> GUI 오픈
 * PersistentDataContainer 태그로 아이템 식별
 */
public class TriggerItemListener implements Listener {

    private final MoonSelectorPlugin plugin;
    private final GUIManager guiManager;

    // 아이템 식별 태그 키
    public static final String TRIGGER_KEY_NAME = "moonselector_trigger";

    public TriggerItemListener(MoonSelectorPlugin plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 우클릭만 처리
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // 메인핸드만 처리 (오프핸드 중복 방지)
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType().isAir()) return;
        if (!isTriggerItem(item)) return;

        event.setCancelled(true);

        // GUI 권한 확인
        if (!player.hasPermission("moonselector.use")) {
            player.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        // 이미 세션이 있으면 무시 (중복 오픈 방지)
        if (guiManager.hasSession(player.getUniqueId())) return;

        guiManager.openSelectorGUI(player);
    }

    /**
     * PersistentDataContainer로 트리거 아이템 여부 확인
     */
    public static boolean isTriggerItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        NamespacedKey key = new NamespacedKey(MoonSelectorPlugin.getInstance(), TRIGGER_KEY_NAME);
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /**
     * 아이템에 트리거 태그를 부여합니다.
     */
    public static ItemStack makeTriggerItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;
        ItemStack tagged = item.clone();
        ItemMeta meta = tagged.getItemMeta();
        if (meta == null) return tagged;

        NamespacedKey key = new NamespacedKey(MoonSelectorPlugin.getInstance(), TRIGGER_KEY_NAME);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

        String configName = MoonSelectorPlugin.getInstance().getConfig()
            .getString("trigger-item.display-name", "&b[아이템 선택기]");
        meta.setDisplayName(MoonSelectorPlugin.colorize(configName));
        tagged.setItemMeta(meta);
        return tagged;
    }
}
