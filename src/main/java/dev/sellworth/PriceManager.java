package dev.sellworth;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PriceManager {
    /** blocker == null -> sellable. Otherwise blocker is the first unsellable item found. */
    public record Appraisal(double value, ItemStack blocker) {}

    private final JavaPlugin plugin;
    private volatile Map<Material, Double> prices = Map.of();

    public PriceManager(JavaPlugin plugin) { this.plugin = plugin; }

    public void load() {
        File f = new File(plugin.getDataFolder(), "prices.yml");
        if (!f.exists()) plugin.saveResource("prices.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        // Supports both "prices:" section format and flat top-level format.
        org.bukkit.configuration.ConfigurationSection sec =
                y.isConfigurationSection("prices") ? y.getConfigurationSection("prices") : y;
        Map<Material, Double> map = new HashMap<>();
        for (String key : sec.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m == null || !m.isItem() || m.isAir()) {
                plugin.getLogger().warning("prices.yml: unknown or non-item material '" + key + "' (skipped)");
                continue;
            }
            if (!sec.isDouble(key) && !sec.isInt(key) && !sec.isLong(key)) {
                plugin.getLogger().warning("prices.yml: price for '" + key + "' is not a number (skipped)");
                continue;
            }
            double v = sec.getDouble(key);
            if (v <= 0) continue; // 0 or negative = unsellable
            map.put(m, v);
        }
        prices = Map.copyOf(map);
        plugin.getLogger().info("Loaded " + prices.size() + " prices.");
    }

    public Map<Material, Double> all() { return prices; }

    public Double get(Material m) { return prices.get(m); }

    public Appraisal appraise(ItemStack item) {
        Double base = prices.get(item.getType());
        if (base == null) return new Appraisal(0, item);

        double total = base * item.getAmount() * durabilityFactor(item);

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inner : box.getInventory().getContents()) {
                if (inner == null || inner.getType().isAir()) continue;
                Appraisal a = appraise(inner);
                if (a.blocker() != null) return a;
                total += a.value();
            }
        }
        return new Appraisal(Math.round(total * 100.0) / 100.0, null);
    }

    /** One sold stack (a shulker box produces one Sale for itself plus one per item inside). */
    public record Sale(Material material, int amount, double value) {}

    /** Assumes the item is sellable (appraise() returned no blocker). */
    public List<Sale> breakdown(ItemStack item) {
        List<Sale> out = new ArrayList<>();
        collect(item, out);
        return out;
    }

    private void collect(ItemStack item, List<Sale> out) {
        Double base = prices.get(item.getType());
        if (base == null) return;
        out.add(new Sale(item.getType(), item.getAmount(),
                Math.round(base * item.getAmount() * durabilityFactor(item) * 100.0) / 100.0));
        if (item.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inner : box.getInventory().getContents()) {
                if (inner != null && !inner.getType().isAir()) collect(inner, out);
            }
        }
    }

    private double durabilityFactor(ItemStack item) {
        int max = item.getType().getMaxDurability();
        if (max <= 0) return 1.0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable d) {
            return Math.max(0.0, Math.min(1.0, (max - d.getDamage()) / (double) max));
        }
        return 1.0;
    }
}
