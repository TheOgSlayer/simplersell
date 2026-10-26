package dev.sellworth;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** Per-player sell statistics, saved to plugins/SellWorth/data/<uuid>.yml */
public class HistoryManager {
    public static final int MAX_HISTORY = 500;

    public record Entry(Material material, int amount, double value, long time) {}

    public static class Data {
        /** material -> {items sold, times sold} */
        public final Map<Material, long[]> totals = new LinkedHashMap<>();
        /** oldest first, newest last */
        public final ArrayDeque<Entry> history = new ArrayDeque<>();
        public double earned;
        public long items;
    }

    private final JavaPlugin plugin;
    private final File dir;
    private final Map<UUID, Data> cache = new HashMap<>();

    public HistoryManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "data");
        dir.mkdirs();
    }

    public Data get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    public void record(UUID id, List<PriceManager.Sale> sales) {
        if (sales.isEmpty()) return;
        Data d = get(id);
        long now = System.currentTimeMillis();
        Set<Material> seen = new HashSet<>();
        for (PriceManager.Sale s : sales) {
            d.history.addLast(new Entry(s.material(), s.amount(), s.value(), now));
            d.items += s.amount();
            d.earned += s.value();
            long[] t = d.totals.computeIfAbsent(s.material(), k -> new long[2]);
            t[0] += s.amount();
            if (seen.add(s.material())) t[1]++; // once per sell action
        }
        while (d.history.size() > MAX_HISTORY) d.history.removeFirst();
        d.earned = Math.round(d.earned * 100.0) / 100.0;
        save(id, d);
    }

    private Data load(UUID id) {
        Data d = new Data();
        File f = new File(dir, id + ".yml");
        if (!f.exists()) return d;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        d.earned = y.getDouble("earned");
        d.items = y.getLong("items");
        ConfigurationSection t = y.getConfigurationSection("totals");
        if (t != null) {
            for (String key : t.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m != null) d.totals.put(m, new long[]{t.getLong(key + ".items"), t.getLong(key + ".times")});
            }
        }
        for (String line : y.getStringList("history")) {
            String[] p = line.split(";");
            if (p.length != 4) continue;
            Material m = Material.matchMaterial(p[0]);
            if (m == null) continue;
            try {
                d.history.addLast(new Entry(m, Integer.parseInt(p[1]), Double.parseDouble(p[2]), Long.parseLong(p[3])));
            } catch (NumberFormatException ignored) {}
        }
        return d;
    }

    private void save(UUID id, Data d) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("earned", d.earned);
        y.set("items", d.items);
        for (Map.Entry<Material, long[]> e : d.totals.entrySet()) {
            y.set("totals." + e.getKey().name() + ".items", e.getValue()[0]);
            y.set("totals." + e.getKey().name() + ".times", e.getValue()[1]);
        }
        List<String> lines = new ArrayList<>();
        for (Entry e : d.history) {
            lines.add(e.material().name() + ";" + e.amount() + ";" + e.value() + ";" + e.time());
        }
        y.set("history", lines);
        String out = y.saveToString();
        File f = new File(dir, id + ".yml");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.writeString(f.toPath(), out);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save " + f.getName() + ": " + ex.getMessage());
            }
        });
    }
}
