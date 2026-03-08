package com.moonselector.listener;

import com.moonselector.MoonSelectorPlugin;
import com.moonselector.manager.GUIManager;
import com.moonselector.model.GUISession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;

/**
 * GUI 인벤토리 이벤트 처리
 * ─ 복사 버그 방지를 위한 엄격한 이벤트 차단 ─
 */
public class GUIListener implements Listener {

    private final MoonSelectorPlugin plugin;
    private final GUIManager guiManager;

    public GUIListener(MoonSelectorPlugin plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    /**
     * 인벤토리 클릭 이벤트
     * HIGHEST 우선순위로 다른 플러그인보다 먼저 처리
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GUISession session = guiManager.getSession(player.getUniqueId());
        if (session == null) return;

        // GUI 인벤토리인지 확인
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) {
            event.setCancelled(true);
            return;
        }

        // ── 복사 버그 방지: 모든 shift 클릭, 드래그, 핫바 조작 차단 ──
        ClickType clickType = event.getClick();
        if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT
                || clickType == ClickType.DOUBLE_CLICK || clickType == ClickType.NUMBER_KEY
                || clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP
                || clickType == ClickType.CREATIVE || clickType == ClickType.MIDDLE
                || clickType == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        boolean isTopInventory = clickedInv.equals(session.getInventory());

        // 하단 플레이어 인벤토리 클릭 차단
        if (!isTopInventory) {
            event.setCancelled(true);
            return;
        }

        // ── GUI 타입별 처리 ──
        switch (session.getType()) {
            case SELECTOR -> handleSelectorClick(event, player, rawSlot);
            case ADMIN_SETUP -> handleAdminClick(event, player, rawSlot);
        }
    }

    private void handleSelectorClick(InventoryClickEvent event, Player player, int slot) {
        // 유저 선택 GUI: 모든 클릭 기본 차단
        event.setCancelled(true);

        GUISession session = guiManager.getSession(player.getUniqueId());
        if (session == null || session.isRewardClaimed()) return;

        if (guiManager.isItemSlot(slot)) {
            // 아이템 슬롯 클릭 -> 선택 처리
            guiManager.handleItemSelect(player, slot);
        } else if (guiManager.isRewardSlot(slot)) {
            // 보상받기 버튼 클릭
            guiManager.handleRewardClaim(player);
        }
        // 그 외 슬롯(필러, 인디케이터 등)은 무시
    }

    private void handleAdminClick(InventoryClickEvent event, Player admin, int slot) {
        GUISession session = guiManager.getSession(admin.getUniqueId());
        if (session == null) return;

        if (guiManager.isSaveSlot(slot)) {
            event.setCancelled(true);
            guiManager.handleAdminSave(admin);
        } else if (guiManager.isClearSlot(slot)) {
            event.setCancelled(true);
            guiManager.handleAdminClear(admin);
        } else if (guiManager.isItemSlot(slot)) {
            // 관리자는 아이템 슬롯에 아이템 넣기/빼기 허용
            // 단, shift 클릭은 이미 위에서 차단됨
            // 기본 클릭(아이템 교환)은 허용
        } else {
            // 기타 슬롯(필러 등) 차단
            event.setCancelled(true);
        }
    }

    /**
     * 인벤토리 드래그 이벤트
     * GUI 위에서 드래그하면 차단
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GUISession session = guiManager.getSession(player.getUniqueId());
        if (session == null) return;

        // GUI 슬롯에 드래그 시도하면 차단
        int topSize = session.getInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }

        // 관리자 GUI에서 플레이어 인벤토리 드래그는 허용
        if (session.getType() == GUISession.SessionType.SELECTOR) {
            event.setCancelled(true);
        }
    }

    /**
     * 인벤토리 닫기 이벤트
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        guiManager.onInventoryClose(player);
    }

    /**
     * 플레이어가 서버에서 나갔을 때 세션 정리
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GUISession session = guiManager.getSession(player.getUniqueId());
        if (session != null) {
            // 관리자가 나갔을 때 아이템 손실 방지 -> 땅에 드롭
            if (session.getType() == GUISession.SessionType.ADMIN_SETUP) {
                for (int slot : new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34}) {
                    var item = session.getInventory().getItem(slot);
                    if (item != null && !item.getType().isAir()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
                    }
                }
            }
            guiManager.onInventoryClose(player);
        }
    }
}
