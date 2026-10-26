package dev.sellworth;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds "Worth: $x" to the tooltip of every priced item. It is done on the packets sent to the
 * client, so the real server-side items are never modified.
 * Marker values: 1 = worth line already added (strippable), 2 = never decorate (GUI buttons).
 */
public class WorthLore implements Listener {
    private final NamespacedKey key;
    private final PriceManager prices;

    public WorthLore(JavaPlugin plugin, PriceManager prices) {
        this.key = new NamespacedKey(plugin, "worth_lore");
        this.prices = prices;
    }

    public static Component line(double value) {
        return Component.text("Worth: ", Text.BLUE)
                .append(Component.text("$", NamedTextColor.GREEN))
                .append(Component.text(Text.money(value), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Mark an item (GUI button/filler) so it never gets a worth line. */
    public ItemStack skip(ItemStack item) {
        ItemMeta m = item.getItemMeta();
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 2);
        item.setItemMeta(m);
        return item;
    }

    /** Returns a copy with the worth line added, or the same item if not applicable. */
    public ItemStack decorate(ItemStack item) {
        try {
            if (item == null || item.getType().isAir()) return item;
            ItemMeta meta = item.getItemMeta();
            if (meta == null || meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return item;

            PriceManager.Appraisal a = prices.appraise(item);
            if (a.blocker() != null) return item;

            ItemStack copy = item.clone();
            ItemMeta m = copy.getItemMeta();
            List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.add(line(a.value()));
            m.lore(lore);
            m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            copy.setItemMeta(m);
            return copy;
        } catch (Throwable t) {
            return item;
        }
    }

    public void registerPackets(JavaPlugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGH,
                PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent e) {
                try {
                    PacketContainer packet = e.getPacket().deepClone();
                    if (e.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                        if (packet.getIntegers().read(0) == -1) return; // cursor
                        packet.getItemModifier().write(0, decorate(packet.getItemModifier().read(0)));
                    } else {
                        List<ItemStack> in = packet.getItemListModifier().read(0);
                        List<ItemStack> out = new ArrayList<>(in.size());
                        for (ItemStack it : in) out.add(decorate(it));
                        packet.getItemListModifier().write(0, out);
                        if (packet.getItemModifier().size() > 0) {
                            packet.getItemModifier().write(0, decorate(packet.getItemModifier().read(0)));
                        }
                    }
                    e.setPacket(packet);
                } catch (Throwable t) {
                    // never break inventory packets
                }
            }
        });
    }

    /** Creative clients send back the tooltip they saw - strip our line so it is never saved on the item. */
    @EventHandler
    public void onCreative(InventoryCreativeEvent e) {
        ItemStack cursor = e.getCursor();
        if (cursor.getType().isAir()) return;
        ItemMeta meta = cursor.getItemMeta();
        if (meta == null) return;
        Byte v = meta.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        if (v == null) return;

        ItemStack copy = cursor.clone();
        ItemMeta m = copy.getItemMeta();
        m.getPersistentDataContainer().remove(key);
        if (v == 1) {
            List<Component> lore = m.lore();
            if (lore != null && !lore.isEmpty()) {
                lore = new ArrayList<>(lore);
                lore.remove(lore.size() - 1);
                m.lore(lore.isEmpty() ? null : lore);
            }
        }
        copy.setItemMeta(m);
        e.setCursor(copy);
    }
}
