package dev.sellworth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class WorthGui implements CommandExecutor, TabCompleter, Listener {
    private static final int PAGE_SIZE = 45;
    private static final int PREV = 45, SORT = 49, NEXT = 53;

    private enum Sort {
        ALPHABETICAL("alphabetical"), HIGH_LOW("highest to lowest"), LOW_HIGH("lowest to highest");
        final String label;
        Sort(String label) { this.label = label; }
        Sort next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static class Holder implements InventoryHolder {
        Inventory inv;
        String filter; // normalized, e.g. "OAK" or null
        Sort sort = Sort.ALPHABETICAL;
        int page = 0;
        @Override public Inventory getInventory() { return inv; }
    }

    private final PriceManager prices;
    private final WorthLore lore;

    public WorthGui(PriceManager prices, WorthLore lore) {
        this.prices = prices;
        this.lore = lore;
    }

    // ---------------------------------------------------------------- command

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }

        if (args.length == 0) { open(p, null); return true; }

        String query = String.join("_", args).toUpperCase(Locale.ROOT);
        Material exact = Material.getMaterial(query);
        if (exact != null && exact.isItem() && !exact.isAir()) {
            Double price = prices.get(exact);
            Text.sendBoth(p, price == null ? Text.unsellable(exact) : Text.worth(exact, price));
            return true;
        }

        // partial name: "oak pla" -> OAK_PLANKS, "diamond pick" -> DIAMOND_PICKAXE
        List<Material> contains = new ArrayList<>();
        List<Material> starts = new ArrayList<>();
        for (Material m : prices.all().keySet()) {
            if (m.name().contains(query)) contains.add(m);
            if (m.name().startsWith(query)) starts.add(m);
        }
        if (contains.isEmpty()) {
            Text.sendBoth(p, Component.text("No items found matching " + String.join(" ", args), NamedTextColor.RED));
            return true;
        }
        Material single = starts.size() == 1 ? starts.get(0) : (contains.size() == 1 ? contains.get(0) : null);
        if (single != null) {
            Text.sendBoth(p, Text.worth(single, prices.get(single)));
            return true;
        }
        open(p, query);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        return Collections.emptyList(); // no suggestions - user types the item name themselves
    }

    // -------------------------------------------------------------------- gui

    private void open(Player p, String filter) {
        Holder h = new Holder();
        h.filter = filter;
        h.inv = Bukkit.createInventory(h, 54, Component.text(Text.smallCaps("item worth")));
        render(h);
        p.openInventory(h.inv);
    }

    private void render(Holder h) {
        List<Material> list = new ArrayList<>();
        for (Material m : prices.all().keySet()) {
            if (h.filter == null || m.name().contains(h.filter)) list.add(m);
        }
        Comparator<Material> byName = Comparator.comparing(Text::itemName);
        switch (h.sort) {
            case ALPHABETICAL -> list.sort(byName);
            case HIGH_LOW -> list.sort(Comparator.<Material>comparingDouble(m -> prices.get(m)).reversed().thenComparing(byName));
            case LOW_HIGH -> list.sort(Comparator.<Material>comparingDouble(m -> prices.get(m)).thenComparing(byName));
        }

        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PAGE_SIZE));
        h.page = Math.max(0, Math.min(h.page, pages - 1));

        Inventory inv = h.inv;
        inv.clear();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = h.page * PAGE_SIZE + i;
            if (idx >= list.size()) break;
            inv.setItem(i, lore.decorate(new ItemStack(list.get(idx))));
        }

        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
        if (h.page > 0) inv.setItem(PREV, named(Material.ARROW, "previous page", null));
        if (h.page < pages - 1) inv.setItem(NEXT, named(Material.ARROW, "next page", null));

        List<Component> sortLore = new ArrayList<>();
        for (Sort s : Sort.values()) {
            boolean cur = s == h.sort;
            sortLore.add(Text.plain((cur ? "▶ " : "  ") + s.label, cur ? Text.BLUE : NamedTextColor.GRAY));
        }
        sortLore.add(Component.empty());
        sortLore.add(Text.plain("page " + (h.page + 1) + "/" + pages, NamedTextColor.DARK_GRAY));
        sortLore.add(Text.plain("click to change sorting", NamedTextColor.DARK_GRAY));
        ItemStack sort = named(Material.HOPPER, "sort", sortLore);
        inv.setItem(SORT, sort);
    }

    private ItemStack named(Material mat, String name, List<Component> loreLines) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(name.isBlank() ? Component.text(" ")
                : Component.text(Text.smallCaps(name), Text.BLUE).decoration(TextDecoration.ITALIC, false));
        if (loreLines != null) m.lore(loreLines);
        it.setItemMeta(m);
        return lore.skip(it);
    }

    // ----------------------------------------------------------------- events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;

        switch (e.getSlot()) {
            case PREV -> { if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) { h.page--; render(h); } }
            case NEXT -> { if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) { h.page++; render(h); } }
            case SORT -> { h.sort = h.sort.next(); h.page = 0; render(h); }
            default -> {}
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
