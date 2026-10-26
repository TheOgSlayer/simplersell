package dev.sellworth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.*;

/** "Sell History" (last 500 sold items) and "Items Sold" (per-item totals, most sold first). */
public class HistoryGui implements CommandExecutor, Listener {
    public enum Mode { HISTORY, ITEMS }

    private static final int PAGE_SIZE = 45;
    private static final int PREV = 45, BACK = 49, NEXT = 53;

    private static class Holder implements InventoryHolder {
        Inventory inv;
        UUID owner;
        Mode mode;
        int page;
        @Override public Inventory getInventory() { return inv; }
    }

    private final JavaPlugin plugin;
    private final HistoryManager history;
    private final WorthLore lore;
    private final SellGui sellGui;

    public HistoryGui(JavaPlugin plugin, HistoryManager history, WorthLore lore, SellGui sellGui) {
        this.plugin = plugin;
        this.history = history;
        this.lore = lore;
        this.sellGui = sellGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        open(p, Mode.HISTORY);
        return true;
    }

    public void open(Player p, Mode mode) {
        Holder h = new Holder();
        h.owner = p.getUniqueId();
        h.mode = mode;
        String title = mode == Mode.HISTORY ? "sell history" : "items sold";
        h.inv = Bukkit.createInventory(h, 54, Component.text(Text.smallCaps(title)));
        render(h);
        p.openInventory(h.inv);
    }

    private void render(Holder h) {
        HistoryManager.Data d = history.get(h.owner);
        List<ItemStack> icons = new ArrayList<>();

        if (h.mode == Mode.HISTORY) {
            SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, HH:mm");
            Iterator<HistoryManager.Entry> it = d.history.descendingIterator(); // newest first
            while (it.hasNext()) {
                HistoryManager.Entry e = it.next();
                icons.add(icon(e.material(), e.amount(), List.of(
                        Text.kv("Amount", Component.text(Text.number(e.amount()), NamedTextColor.WHITE)),
                        Text.kv("Earned", Text.dollar(Text.fullMoney(e.value()))),
                        Text.kv("Sold", Component.text(fmt.format(new Date(e.time())), NamedTextColor.GRAY)))));
            }
        } else {
            List<Map.Entry<Material, long[]>> list = new ArrayList<>(d.totals.entrySet());
            list.sort((a, b) -> {
                int c = Long.compare(b.getValue()[0], a.getValue()[0]);
                return c != 0 ? c : a.getKey().name().compareTo(b.getKey().name());
            });
            for (Map.Entry<Material, long[]> e : list) {
                icons.add(icon(e.getKey(), 1, List.of(
                        Text.kv("Items sold", Component.text(Text.number(e.getValue()[0]), NamedTextColor.WHITE)),
                        Text.kv("Times sold", Component.text(Text.number(e.getValue()[1]), NamedTextColor.WHITE)))));
            }
        }

        int pages = Math.max(1, (int) Math.ceil(icons.size() / (double) PAGE_SIZE));
        h.page = Math.max(0, Math.min(h.page, pages - 1));

        Inventory inv = h.inv;
        inv.clear();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = h.page * PAGE_SIZE + i;
            if (idx >= icons.size()) break;
            inv.setItem(i, icons.get(idx));
        }

        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, null);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        if (h.page > 0) inv.setItem(PREV, button(Material.ARROW, "Previous Page", Text.BLUE, null));
        if (h.page < pages - 1) inv.setItem(NEXT, button(Material.ARROW, "Next Page", Text.BLUE, null));

        List<Component> backLore = new ArrayList<>();
        backLore.add(Text.gray("Page " + (h.page + 1) + "/" + pages));
        if (h.mode == Mode.HISTORY) {
            backLore.add(Text.gray("Showing newest first"));
        } else {
            backLore.add(Text.kv("Total items sold", Component.text(Text.number(d.items), NamedTextColor.WHITE)));
            backLore.add(Text.gray("Showing most sold first"));
        }
        backLore.add(Component.empty());
        backLore.add(Text.gray("Click to go back to /sell"));
        inv.setItem(BACK, button(Material.BARRIER, "Back", NamedTextColor.RED, backLore));
    }

    private ItemStack icon(Material m, int amount, List<Component> lines) {
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(amount, m.getMaxStackSize())));
        ItemMeta meta = it.getItemMeta();
        meta.lore(lines);
        it.setItemMeta(meta);
        return lore.skip(it);
    }

    private ItemStack button(Material mat, String name, TextColor color, List<Component> lines) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (lines != null) m.lore(lines);
        it.setItemMeta(m);
        return lore.skip(it);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cur = e.getCurrentItem();
        if (cur == null) return;

        switch (e.getSlot()) {
            case PREV -> { if (cur.getType() == Material.ARROW) { h.page--; render(h); } }
            case NEXT -> { if (cur.getType() == Material.ARROW) { h.page++; render(h); } }
            case BACK -> { if (cur.getType() == Material.BARRIER) Bukkit.getScheduler().runTask(plugin, () -> sellGui.open(p)); }
            default -> {}
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
