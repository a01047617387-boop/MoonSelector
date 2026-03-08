package com.moonselector.manager;

import com.moonselector.MoonSelectorPlugin;
import com.moonselector.model.GUISession;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI 생성 및 세션 관리를 담당합니다.
 * 복사 버그 방지를 위해 세션 기반으로 엄격하게 관리합니다.
 */
public class GUIManager {

    private final MoonSelectorPlugin plugin;
    // 플레이어 UUID -> GUI 세션 (활성 세션만 등록)
    private final Map<UUID, GUISession> activeSessions = new HashMap<>();

    // GUI 레이아웃 상수
    private static final int GUI_ROWS = 6;
    private static final int GUI_SIZE = GUI_ROWS * 9; // 54
    private static final int REWARD_SLOT = 49;         // 하단 중앙
    // 아이템이 표시될 슬롯들 (1~3행, 양쪽 테두리 제외)
    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    public GUIManager(MoonSelectorPlugin plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────────
    // 유저용 아이템 선택 GUI 열기
    // ─────────────────────────────────────────────────────────────

    public void openSelectorGUI(Player player) {
        List<ItemStack> items = plugin.getItemManager().getItems();

        if (items.isEmpty()) {
            MoonSelectorPlugin.sendMessage(player, plugin.getMessage("no-items-configured"));
            return;
        }

        // 기존 세션이 있으면 정리
        closeSessionSilently(player.getUniqueId());

        String title = MoonSelectorPlugin.colorize(
            plugin.getConfig().getString("gui.title", "&6✦ 아이템 선택 ✦")
        );
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        // 빈 칸 채우기
        fillBackground(inv);

        // 아이템 배치 - GUI 표시용 복사본만 사용 (원본 데이터 절대 오염 안 됨)
        int count = Math.min(items.size(), ITEM_SLOTS.length);
        for (int i = 0; i < count; i++) {
            // GUI 표시용: 수량/인챈트/네임/로어 완벽 보존 + 선택 안내 lore만 추가
            ItemStack display = createDisplayItem(items.get(i), false);
            inv.setItem(ITEM_SLOTS[i], display);
        }

        // 보상받기 버튼 (초기 비활성 상태)
        inv.setItem(REWARD_SLOT, createRewardButton(false));

        // 세션 등록
        GUISession session = new GUISession(player.getUniqueId(), inv, GUISession.SessionType.SELECTOR);
        activeSessions.put(player.getUniqueId(), session);

        player.openInventory(inv);
    }

    // ─────────────────────────────────────────────────────────────
    // 관리자용 아이템 설정 GUI 열기
    // ─────────────────────────────────────────────────────────────

    public void openAdminSetupGUI(Player admin) {
        // 기존 세션 정리
        closeSessionSilently(admin.getUniqueId());

        String title = MoonSelectorPlugin.colorize("&c[관리자] 아이템 설정");
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        // 배경 채우기 (관리자 GUI는 특별한 배경)
        fillAdminBackground(inv);

        // 현재 설정된 아이템 표시 (있으면)
        List<ItemStack> existingItems = plugin.getItemManager().getItems();
        int count = Math.min(existingItems.size(), ITEM_SLOTS.length);
        for (int i = 0; i < count; i++) {
            inv.setItem(ITEM_SLOTS[i], existingItems.get(i).clone());
        }

        // 저장 버튼
        inv.setItem(REWARD_SLOT, createSaveButton());

        // 초기화 버튼
        inv.setItem(48, createClearButton());

        // 안내 아이템
        inv.setItem(50, createInfoItem());

        GUISession session = new GUISession(admin.getUniqueId(), inv, GUISession.SessionType.ADMIN_SETUP);
        activeSessions.put(admin.getUniqueId(), session);

        admin.openInventory(inv);
    }

    // ─────────────────────────────────────────────────────────────
    // 아이템 선택 처리
    // ─────────────────────────────────────────────────────────────

    /**
     * 유저가 아이템 슬롯을 클릭했을 때 선택 처리
     * @return 선택 성공 여부
     */
    public boolean handleItemSelect(Player player, int clickedSlot) {
        GUISession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.getType() != GUISession.SessionType.SELECTOR) return false;
        if (session.isProcessing()) return false;
        if (session.isRewardClaimed()) return false;

        // 클릭된 슬롯이 아이템 슬롯인지 확인
        int itemIndex = getItemIndexFromSlot(clickedSlot);
        if (itemIndex < 0) return false;

        // 아이템이 실제로 있는지 확인
        ItemStack clicked = session.getInventory().getItem(clickedSlot);
        if (clicked == null || clicked.getType().isAir()) return false;

        Inventory inv = session.getInventory();
        int prevSelectedSlot = session.getSelectedSlot();

        // 이전 선택 해제 - 원본 데이터에서 재생성 (lore/meta 오염 방지)
        if (prevSelectedSlot >= 0) {
            int prevIndex = getItemIndexFromSlot(prevSelectedSlot);
            if (prevIndex >= 0) {
                ItemStack prevOriginal = plugin.getItemManager().getItem(prevIndex);
                if (prevOriginal != null) {
                    inv.setItem(prevSelectedSlot, createDisplayItem(prevOriginal, false));
                }
            }
            clearIndicators(inv);
        }

        // 새 선택 표시 - 원본 데이터에서 불러와 선택 lore만 추가한 표시용 생성
        session.setSelectedSlot(clickedSlot);
        ItemStack selectedDisplay = plugin.getItemManager().getItem(itemIndex);
        if (selectedDisplay != null) {
            inv.setItem(clickedSlot, createDisplayItem(selectedDisplay, true));
        }

        // 선택 인디케이터 주변에 배치
        placeIndicators(inv, clickedSlot);

        // 보상받기 버튼 활성화 (선택 아이템 정보 표시)
        inv.setItem(REWARD_SLOT, createRewardButton(true, plugin.getItemManager().getItem(itemIndex)));

        return true;
    }

    /**
     * 보상받기 버튼 처리 (복사 버그 핵심 방지 로직)
     * @return 보상 지급 성공 여부
     */
    public boolean handleRewardClaim(Player player) {
        GUISession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.getType() != GUISession.SessionType.SELECTOR) return false;

        // ── 중복 처리 방지 (isProcessing 플래그) ──
        if (session.isProcessing()) return false;
        if (session.isRewardClaimed()) {
            MoonSelectorPlugin.sendMessage(player, plugin.getMessage("already-received"));
            return false;
        }
        if (!session.hasSelected()) {
            MoonSelectorPlugin.sendMessage(player, plugin.getMessage("no-item-selected"));
            return false;
        }

        // 처리 시작 플래그
        session.setProcessing(true);

        try {
            int selectedSlot = session.getSelectedSlot();
            Inventory inv = session.getInventory();

            // GUI에서 아이템 가져오기 (인덱스로 원본 데이터에서 직접 가져옴)
            int itemIndex = getItemIndexFromSlot(selectedSlot);
            if (itemIndex < 0) {
                session.setProcessing(false);
                return false;
            }

            // 원본 매니저에서 방어적 복사본 가져오기 (GUI에서 직접 꺼내지 않음!)
            ItemStack reward = plugin.getItemManager().getItem(itemIndex);
            if (reward == null || reward.getType().isAir()) {
                session.setProcessing(false);
                MoonSelectorPlugin.sendMessage(player, plugin.getMessage("no-item-selected"));
                return false;
            }

            // 인벤토리 공간 확인
            if (!hasInventorySpace(player, reward)) {
                session.setProcessing(false);
                MoonSelectorPlugin.sendMessage(player, plugin.getMessage("inventory-full"));
                return false;
            }

            // 인벤토리 꽉 차면 지급 차단 - GUI는 열린 채로 유지 (재시도 가능)
            session.setRewardClaimed(true);

            // 아이템 지급 - 수량 포함한 완전한 복사본 지급
            // 수량이 maxStack 초과면 여러 스택으로 나눠서 지급
            int remaining = reward.getAmount();
            int maxStack = reward.getMaxStackSize();
            while (remaining > 0) {
                ItemStack chunk = reward.clone();
                chunk.setAmount(Math.min(remaining, maxStack));
                player.getInventory().addItem(chunk);
                remaining -= maxStack;
            }
            MoonSelectorPlugin.sendMessage(player, plugin.getMessage("reward-received"));

            // GUI 닫기 (다음 틱에 처리, 이벤트 내에서 직접 닫으면 오류 가능)
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                activeSessions.remove(player.getUniqueId());
            });

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("보상 지급 중 오류: " + e.getMessage());
            session.setProcessing(false);
            session.setRewardClaimed(false);
            return false;
        }
    }

    /**
     * 관리자 GUI 저장 처리
     */
    public boolean handleAdminSave(Player admin) {
        GUISession session = activeSessions.get(admin.getUniqueId());
        if (session == null || session.getType() != GUISession.SessionType.ADMIN_SETUP) return false;
        if (session.isProcessing()) return false;

        session.setProcessing(true);

        try {
            Inventory inv = session.getInventory();
            List<ItemStack> newItems = new ArrayList<>();

            for (int slot : ITEM_SLOTS) {
                ItemStack item = inv.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    // GUI 태그 lore 완전 제거 후 저장 (인챈트/네임/로어/수량 완벽 보존)
                    ItemStack clean = cleanForStorage(item);
                    if (clean != null) newItems.add(clean);
                }
            }

            plugin.getItemManager().setItems(newItems);

            int count = newItems.size();
            MoonSelectorPlugin.sendMessage(admin, plugin.getMessage("item-set", "%count%", String.valueOf(count)));

            Bukkit.getScheduler().runTask(plugin, () -> {
                admin.closeInventory();
                activeSessions.remove(admin.getUniqueId());
            });

            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("아이템 저장 중 오류: " + e.getMessage());
            session.setProcessing(false);
            return false;
        }
    }

    /**
     * 관리자 GUI 초기화 처리
     */
    public boolean handleAdminClear(Player admin) {
        GUISession session = activeSessions.get(admin.getUniqueId());
        if (session == null || session.getType() != GUISession.SessionType.ADMIN_SETUP) return false;
        if (session.isProcessing()) return false;

        Inventory inv = session.getInventory();
        for (int slot : ITEM_SLOTS) {
            inv.setItem(slot, createFillerItem());
        }

        plugin.getItemManager().clearItems();
        MoonSelectorPlugin.sendMessage(admin, plugin.getMessage("item-cleared"));
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // 세션 관리
    // ─────────────────────────────────────────────────────────────

    public GUISession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    public boolean hasSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    /**
     * 플러그인 비활성화 시 모든 세션 정리
     */
    public void closeAllSessions() {
        for (UUID uuid : new HashSet<>(activeSessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                GUISession session = activeSessions.get(uuid);
                // 관리자 GUI가 열린 채로 서버가 꺼지면 아이템 손실 방지
                if (session != null && session.getType() == GUISession.SessionType.ADMIN_SETUP) {
                    returnAdminItems(player, session);
                }
                player.closeInventory();
            }
        }
        activeSessions.clear();
    }

    /**
     * GUI 닫기 이벤트 처리 (InventoryCloseEvent)
     * 복사 버그 방지: 관리자 GUI 아이템을 인벤토리에서 제거
     */
    public void onInventoryClose(Player player) {
        GUISession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (session.getType() == GUISession.SessionType.ADMIN_SETUP) {
            // 처리 중이면 (save/clear 버튼 눌러서 닫는 경우) 세션은 이미 제거됨
            // 아니면 강제로 닫힌 것 -> 아이템 반환
            if (!session.isProcessing()) {
                returnAdminItems(player, session);
            }
        }

        // 세션 정리 (처리 중 플래그가 있는 경우는 handleRewardClaim에서 처리)
        if (!session.isProcessing()) {
            activeSessions.remove(player.getUniqueId());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 내부 유틸리티
    // ─────────────────────────────────────────────────────────────

    private void closeSessionSilently(UUID uuid) {
        GUISession old = activeSessions.remove(uuid);
        if (old != null) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                // 이전 세션 GUI가 열려있으면 닫기
                if (p.getOpenInventory().getTopInventory().equals(old.getInventory())) {
                    p.closeInventory();
                }
            }
        }
    }

    /**
     * 관리자 GUI에 넣은 아이템을 플레이어에게 반환합니다.
     */
    private void returnAdminItems(Player admin, GUISession session) {
        Inventory inv = session.getInventory();
        for (int slot : ITEM_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                // 아이템 반환 (인벤토리 가득 찼으면 바닥에 드롭)
                Map<Integer, ItemStack> overflow = admin.getInventory().addItem(item.clone());
                for (ItemStack dropped : overflow.values()) {
                    admin.getWorld().dropItemNaturally(admin.getLocation(), dropped);
                }
                inv.setItem(slot, null);
            }
        }
    }

    private int getItemIndexFromSlot(int slot) {
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    public boolean isItemSlot(int slot) {
        return getItemIndexFromSlot(slot) >= 0;
    }

    public boolean isRewardSlot(int slot) {
        return slot == REWARD_SLOT;
    }

    public boolean isSaveSlot(int slot) {
        return slot == REWARD_SLOT;
    }

    public boolean isClearSlot(int slot) {
        return slot == 48;
    }

    private boolean hasInventorySpace(Player player, ItemStack item) {
        if (item == null) return false;
        // 테스트용 인벤토리로 확인 (실제 인벤토리를 건드리지 않음)
        // 수량이 64 초과일 경우에도 정확하게 검사
        Inventory testInv = Bukkit.createInventory(null, 36);
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot != null) {
                testInv.setItem(i, slot.clone());
            }
        }
        // 수량이 maxStack 초과면 여러 스택으로 나눠서 체크
        int amount = item.getAmount();
        int maxStack = item.getMaxStackSize();
        while (amount > 0) {
            ItemStack chunk = item.clone();
            chunk.setAmount(Math.min(amount, maxStack));
            Map<Integer, ItemStack> result = testInv.addItem(chunk);
            if (!result.isEmpty()) return false;
            amount -= maxStack;
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // GUI 아이템 생성
    // ─────────────────────────────────────────────────────────────

    private void fillBackground(Inventory inv) {
        ItemStack filler = createFillerItem();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }
    }

    private void fillAdminBackground(Inventory inv) {
        ItemStack filler = createNamedItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }
        // 아이템 슬롯은 비우기
        for (int slot : ITEM_SLOTS) {
            inv.setItem(slot, null);
        }
    }

    private ItemStack createFillerItem() {
        String mat = plugin.getConfig().getString("filler.material", "GRAY_STAINED_GLASS_PANE");
        String name = plugin.getConfig().getString("filler.display-name", " ");
        Material material = Material.matchMaterial(mat);
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
        return createNamedItem(material, name);
    }

    private ItemStack createRewardButton(boolean active) {
        return createRewardButton(active, null);
    }

    /**
     * 보상받기 버튼 생성
     * selectedItem이 있으면 선택된 아이템의 이름/수량 표시
     */
    private ItemStack createRewardButton(boolean active, ItemStack selectedItem) {
        Material mat = active ? Material.LIME_WOOL : Material.GRAY_WOOL;
        String name = active
            ? MoonSelectorPlugin.colorize("&a✔ 보상받기 &7(클릭!)")
            : MoonSelectorPlugin.colorize("&7✘ 아이템을 선택하세요");
        ItemStack btn = new ItemStack(mat);
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            if (active && selectedItem != null) {
                // 선택된 아이템 정보 표시
                String itemName = selectedItem.hasItemMeta() && selectedItem.getItemMeta().hasDisplayName()
                    ? selectedItem.getItemMeta().getDisplayName()
                    : selectedItem.getType().name();
                lore.add(MoonSelectorPlugin.colorize("&7선택한 아이템: &f" + itemName));
                lore.add(MoonSelectorPlugin.colorize("&7수량: &f" + selectedItem.getAmount() + "개"));
                // 인챈트 요약 표시
                if (selectedItem.hasItemMeta() && !selectedItem.getItemMeta().getEnchants().isEmpty()) {
                    lore.add(MoonSelectorPlugin.colorize("&9✦ 인챈트 " + selectedItem.getItemMeta().getEnchants().size() + "종 포함"));
                }
                lore.add(MoonSelectorPlugin.colorize("&e클릭하여 수령!"));
            } else if (active) {
                lore.add(MoonSelectorPlugin.colorize("&e선택한 아이템을 인벤토리로 받습니다."));
            } else {
                lore.add(MoonSelectorPlugin.colorize("&7위의 아이템 중 하나를 선택하면"));
                lore.add(MoonSelectorPlugin.colorize("&7버튼이 활성화됩니다."));
            }
            meta.setLore(lore);
            btn.setItemMeta(meta);
        }
        return btn;
    }

    private ItemStack createSaveButton() {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MoonSelectorPlugin.colorize("&a✔ 저장하기"));
            meta.setLore(List.of(
                MoonSelectorPlugin.colorize("&7위 슬롯에 아이템을 넣은 후"),
                MoonSelectorPlugin.colorize("&7클릭하여 저장합니다.")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createClearButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MoonSelectorPlugin.colorize("&c✘ 아이템 초기화"));
            meta.setLore(List.of(
                MoonSelectorPlugin.colorize("&c모든 설정 아이템을 삭제합니다."),
                MoonSelectorPlugin.colorize("&c이 작업은 되돌릴 수 없습니다!")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MoonSelectorPlugin.colorize("&e📖 사용 안내"));
            meta.setLore(List.of(
                MoonSelectorPlugin.colorize("&7■ 아이템 슬롯에 원하는 아이템을 배치하세요."),
                MoonSelectorPlugin.colorize("&7■ 저장하기 버튼을 눌러 저장합니다."),
                MoonSelectorPlugin.colorize("&7■ 유저가 NPC 혹은 트리거 아이템을 우클릭하면"),
                MoonSelectorPlugin.colorize("&7  선택 GUI가 열립니다."),
                MoonSelectorPlugin.colorize("&cGUI를 그냥 닫으면 아이템이 반환됩니다.")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNamedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MoonSelectorPlugin.colorize(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    // 선택 인디케이터 배치 (선택된 아이템 주변에 초록 유리 표시)
    private void placeIndicators(Inventory inv, int selectedSlot) {
        clearIndicators(inv);
        Material indicatorMat = Material.LIME_STAINED_GLASS_PANE;
        int[] neighbors = getNeighborSlots(selectedSlot);
        for (int neighbor : neighbors) {
            ItemStack current = inv.getItem(neighbor);
            // 빈 칸이나 필러만 인디케이터로 교체
            if (current == null || isFiller(current)) {
                inv.setItem(neighbor, createNamedItem(indicatorMat, "&a✔"));
            }
        }
    }

    private void clearIndicators(Inventory inv) {
        for (int i = 0; i < GUI_SIZE; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.LIME_STAINED_GLASS_PANE
                    && !isItemSlot(i) && i != REWARD_SLOT) {
                inv.setItem(i, createFillerItem());
            }
        }
    }

    private int[] getNeighborSlots(int slot) {
        List<Integer> neighbors = new ArrayList<>();
        int row = slot / 9;
        int col = slot % 9;
        int[][] offsets = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for (int[] off : offsets) {
            int nr = row + off[0];
            int nc = col + off[1];
            if (nr >= 0 && nr < GUI_ROWS && nc >= 0 && nc < 9) {
                int n = nr * 9 + nc;
                if (!isItemSlot(n) && n != REWARD_SLOT) {
                    neighbors.add(n);
                }
            }
        }
        return neighbors.stream().mapToInt(i -> i).toArray();
    }

    private boolean isFiller(ItemStack item) {
        if (item == null) return true;
        return item.getType() == Material.GRAY_STAINED_GLASS_PANE
            || item.getType() == Material.BLACK_STAINED_GLASS_PANE;
    }

    // ─────────────────────────────────────────────────────────────
    // 아이템 표시/저장 유틸리티
    // ─────────────────────────────────────────────────────────────

    private static final String SELECTION_LORE_SELECTED   = "§a§l▶ 선택됨 ◀";
    private static final String SELECTION_LORE_UNSELECTED = "§7클릭하여 선택";
    // 두 태그 모두 식별용 prefix로 시작
    private static final String LORE_TAG_PREFIX = "§r§0§ItemSel§";

    /**
     * GUI 표시용 아이템을 생성합니다.
     * ─ 원본의 인챈트/네임/로어/수량/NBT 완벽 보존 ─
     * ─ 선택 상태 안내 lore만 맨 끝에 추가 ─
     * ─ 원본 ItemStack은 절대 수정하지 않음 ─
     */
    private ItemStack createDisplayItem(ItemStack original, boolean selected) {
        if (original == null) return null;
        // 완전한 방어적 복사 (수량/인챈트/네임/로어/NBT 모두 포함)
        ItemStack display = original.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        // 기존 lore 가져오기 (원본 lore 보존)
        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // 이전에 추가된 GUI 태그 제거 (혹시 오염된 경우 방어)
        lore.removeIf(line -> line != null && line.startsWith(LORE_TAG_PREFIX));

        // 구분선 + 선택 상태 표시 추가
        lore.add(LORE_TAG_PREFIX + "sep§" + "§8§m──────────────");
        if (selected) {
            lore.add(LORE_TAG_PREFIX + "state§" + SELECTION_LORE_SELECTED);
            lore.add(LORE_TAG_PREFIX + "hint§" + "§e아래 보상받기 버튼을 클릭하세요!");
        } else {
            lore.add(LORE_TAG_PREFIX + "state§" + SELECTION_LORE_UNSELECTED);
        }
        // 수량 표시 (1개 초과 시)
        if (original.getAmount() > 1) {
            lore.add(LORE_TAG_PREFIX + "qty§" + "§7수량: §f" + original.getAmount() + "개");
        }

        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    /**
     * 관리자 GUI 저장 시 GUI 태그 lore를 완전히 제거합니다.
     * 저장본은 원본 그대로 유지됩니다.
     */
    private ItemStack cleanForStorage(ItemStack item) {
        if (item == null) return null;
        ItemStack clean = item.clone();
        ItemMeta meta = clean.getItemMeta();
        if (meta == null) return clean;
        List<String> lore = meta.getLore();
        if (lore == null) return clean;
        lore.removeIf(line -> line != null && line.startsWith(LORE_TAG_PREFIX));
        // 구분선도 제거 (마지막에 남은 공백 정리)
        while (!lore.isEmpty() && lore.get(lore.size() - 1).isBlank()) {
            lore.remove(lore.size() - 1);
        }
        if (lore.isEmpty()) meta.setLore(null);
        else meta.setLore(lore);
        clean.setItemMeta(meta);
        return clean;
    }
}
