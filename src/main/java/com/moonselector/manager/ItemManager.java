package com.moonselector.manager;

import com.moonselector.MoonSelectorPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * 아이템 저장/불러오기 매니저
 * items.yml에 관리자가 설정한 아이템 목록을 저장합니다.
 */
public class ItemManager {

    private final MoonSelectorPlugin plugin;
    private File itemFile;
    private FileConfiguration itemConfig;
    private final List<ItemStack> configuredItems = new ArrayList<>();

    public ItemManager(MoonSelectorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * items.yml 파일에서 아이템 목록을 로드합니다.
     */
    public void loadItems() {
        itemFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                itemFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "items.yml 생성 실패!", e);
                return;
            }
        }

        itemConfig = YamlConfiguration.loadConfiguration(itemFile);
        configuredItems.clear();

        List<?> rawList = itemConfig.getList("items");
        if (rawList == null) return;

        for (Object obj : rawList) {
            if (obj instanceof ItemStack item) {
                if (item != null && !item.getType().isAir()) {
                    // 방어적 복사: 저장된 아이템은 불변으로 관리
                    configuredItems.add(item.clone());
                }
            }
        }

        plugin.getLogger().info("아이템 " + configuredItems.size() + "개 로드 완료.");
    }

    /**
     * 아이템 목록을 items.yml에 저장합니다.
     */
    public void saveItems() {
        if (itemConfig == null || itemFile == null) return;

        // 방어적 복사본 저장
        List<ItemStack> toSave = new ArrayList<>();
        for (ItemStack item : configuredItems) {
            if (item != null && !item.getType().isAir()) {
                toSave.add(item.clone());
            }
        }

        itemConfig.set("items", toSave);
        try {
            itemConfig.save(itemFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "items.yml 저장 실패!", e);
        }
    }

    /**
     * 아이템 목록을 설정합니다. (관리자 전용)
     * 항상 방어적 복사를 수행합니다.
     */
    public void setItems(List<ItemStack> items) {
        configuredItems.clear();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                configuredItems.add(item.clone());
            }
        }
        saveItems();
    }

    /**
     * 설정된 아이템 목록의 불변 복사본을 반환합니다.
     * 외부에서 수정할 수 없도록 방어적 복사를 수행합니다.
     */
    public List<ItemStack> getItems() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack item : configuredItems) {
            if (item != null && !item.getType().isAir()) {
                copy.add(item.clone());
            }
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * 특정 인덱스의 아이템 복사본을 반환합니다.
     */
    public ItemStack getItem(int index) {
        if (index < 0 || index >= configuredItems.size()) return null;
        ItemStack item = configuredItems.get(index);
        return (item != null && !item.getType().isAir()) ? item.clone() : null;
    }

    public int getItemCount() {
        return configuredItems.size();
    }

    public boolean hasItems() {
        return !configuredItems.isEmpty();
    }

    /**
     * 설정된 아이템 목록을 초기화합니다.
     */
    public void clearItems() {
        configuredItems.clear();
        saveItems();
    }
}
