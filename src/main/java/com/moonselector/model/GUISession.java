package com.moonselector.model;

import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * 플레이어의 GUI 세션 정보를 저장합니다.
 * 복사 버그 방지를 위해 세션 상태를 엄격하게 관리합니다.
 */
public class GUISession {

    public enum SessionType {
        SELECTOR,       // 아이템 선택 GUI (유저용)
        ADMIN_SETUP     // 아이템 설정 GUI (관리자용)
    }

    private final UUID playerUUID;
    private final Inventory inventory;
    private final SessionType type;
    private int selectedSlot = -1;          // 선택된 아이템 슬롯 (-1 = 미선택)
    private boolean rewardClaimed = false;  // 보상 수령 여부
    private boolean isProcessing = false;   // 처리 중 여부 (중복 클릭 방지)

    public GUISession(UUID playerUUID, Inventory inventory, SessionType type) {
        this.playerUUID = playerUUID;
        this.inventory = inventory;
        this.type = type;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public Inventory getInventory() { return inventory; }
    public SessionType getType() { return type; }
    public int getSelectedSlot() { return selectedSlot; }
    public boolean isRewardClaimed() { return rewardClaimed; }
    public boolean isProcessing() { return isProcessing; }

    public void setSelectedSlot(int slot) { this.selectedSlot = slot; }
    public void setRewardClaimed(boolean claimed) { this.rewardClaimed = claimed; }
    public void setProcessing(boolean processing) { this.isProcessing = processing; }

    public boolean hasSelected() { return selectedSlot >= 0; }
}
