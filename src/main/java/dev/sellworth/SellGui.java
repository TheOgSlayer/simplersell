package dev.sellworth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SellGui implements CommandExecutor, Listener {
    private static final int ITEM_SLOTS = 45;     // slots 0-44 hold items to sell
    private static final int HISTORY_SLOT = 45;   // bottom left
    private static final int ITEMS_SLOT = 49;     // bottom middle
    private static final int SELL_SLOT = 53;      // bottom right

    private static class Holder implements InventoryHolder {
        Inventory inv;
        UUID owner;
        @Override public Inventory getInventory() { return inv; }
    }

    private final SellWorthPlugin plugin;
    private final PriceManager prices;
    private final WorthLore lore;
    private final HistoryManager history;
    private HistoryGui historyGui;

    public SellGui(SellWorthPlugin plugin, PriceManager prices, WorthLore lore, HistoryManager history) {
        this.plugin = plugin;
        this.prices = prices;
        this.lore = lore;
        this.history = history;
    }

    public void setHistoryGui(HistoryGui g) { this.historyGui = g; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        open(p);
        return true;
    }

    public void open(Player p) {
        Holder h = new Holder();
        h.owner = p.getUniqueId();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text(Text.smallCaps("place items here to sell")));
        h.inv = inv;
        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, null);
        for (int i = ITEM_SLOTS; i < 54; i++) inv.setItem(i, filler);
        refresh(inv, h.owner);
        p.openInventory(inv);
    }

    private ItemStack button(Material mat, String name, TextColor color, List<Component> lines) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (lines != null) m.lore(lines);
        it.setItemMeta(m);
        return lore.skip(it);
    }

    /** Rebuilds the three buttons (live "Selling for" total + stats). */
    private void refresh(Inventory inv, UUID owner) {
        double total = 0;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            PriceManager.Appraisal a = prices.appraise(it);
            if (a.blocker() == null) total += a.value();
        }
        total = Math.round(total * 100.0) / 100.0;

        HistoryManager.Data d = history.get(owner);

        ItemStack sell = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta sm = sell.getItemMeta();
        sm.displayName(Component.text("Sell", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        sm.lore(List.of(Text.kv("Selling for", Text.dollar(Text.fullMoney(total)))));
        sell.setItemMeta(sm);
        inv.setItem(SELL_SLOT, lore.skip(sell));

        inv.setItem(HISTORY_SLOT, button(Material.BOOK, "Sell History", Text.BLUE, List.of(
                Text.gray("Your last " + HistoryManager.MAX_HISTORY + " sold items"),
                Text.kv("Recorded", Component.text(Text.number(d.history.size()), NamedTextColor.WHITE)),
                Component.empty(),
                Text.gray("Click to view"))));

        inv.setItem(ITEMS_SLOT, button(Material.CHEST, "Items Sold", Text.BLUE, List.of(
                Text.kv("Total items sold", Component.text(Text.number(d.items), NamedTextColor.WHITE)),
                Text.kv("Total earned", Text.dollar(Text.fullMoney(d.earned))),
                Component.empty(),
                Text.gray("Click to view (most sold first)"))));
    }

    private void refreshNextTick(Inventory inv, UUID owner) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (inv.getHolder() instanceof Holder) refresh(inv, owner);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        refreshNextTick(top, h.owner);

        boolean inTop = e.getClickedInventory() == top;
        if (inTop && e.getSlot() >= ITEM_SLOTS) {
            e.setCancelled(true);
            switch (e.getSlot()) {
                case SELL_SLOT -> sell(p, top, h);
                case HISTORY_SLOT -> Bukkit.getScheduler().runTask(plugin, () -> historyGui.open(p, HistoryGui.Mode.HISTORY));
                case ITEMS_SLOT -> Bukkit.getScheduler().runTask(plugin, () -> historyGui.open(p, HistoryGui.Mode.ITEMS));
                default -> {}
            }
            return;
        }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        for (int slot : e.getRawSlots()) {
            if (slot >= ITEM_SLOTS && slot < 54) { e.setCancelled(true); return; }
        }
        refreshNextTick(top, h.owner);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof Holder) || !(e.getPlayer() instanceof Player p)) return;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            inv.setItem(i, null);
            for (ItemStack left : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }

    private void sell(Player p, Inventory inv, Holder h) {
        Economy eco = plugin.economy();
        if (eco == null) {
            p.sendMessage(Component.text("No economy plugin found", NamedTextColor.RED));
            return;
        }
        double total = 0;
        Set<Material> blocked = new LinkedHashSet<>();
        List<PriceManager.Sale> sales = new ArrayList<>();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            PriceManager.Appraisal a = prices.appraise(it);
            if (a.blocker() != null) {
                blocked.add(a.blocker().getType());
                continue; // stays in the GUI, returned on close
            }
            total += a.value();
            sales.addAll(prices.breakdown(it));
            inv.setItem(i, null);
        }
        total = Math.round(total * 100.0) / 100.0;

        for (Material m : blocked) Text.sendBoth(p, Text.unsellable(m));
        if (total > 0) {
            eco.depositPlayer(p, total);
            history.record(p.getUniqueId(), sales);
            Text.sendBoth(p, Text.sold(total));
        }
        refresh(inv, h.owner);
    }
}
