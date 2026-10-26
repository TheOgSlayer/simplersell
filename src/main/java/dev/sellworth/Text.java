package dev.sellworth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.math.RoundingMode;
import java.util.Locale;

public final class Text {
    public static final TextColor BLUE = TextColor.fromHexString("#00a2ff");
    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private Text() {}

    public static String smallCaps(String in) {
        StringBuilder sb = new StringBuilder();
        for (char c : in.toCharArray()) {
            int i = NORMAL.indexOf(Character.toLowerCase(c));
            sb.append(i >= 0 ? SMALL.charAt(i) : c);
        }
        return sb.toString();
    }

    public static String itemName(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static final String[] SUFFIX = {"", "K", "M", "B", "T", "Qa", "Qi"};
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    /** Short form, max 2 decimals (rounded down): 1000 -> 1K, 1221 -> 1.22K, 1000000 -> 1M, 38.89 -> 38.89 */
    public static String money(double v) {
        BigDecimal bd = BigDecimal.valueOf(v);
        int i = 0;
        while (bd.abs().compareTo(THOUSAND) >= 0 && i < SUFFIX.length - 1) {
            bd = bd.movePointLeft(3);
            i++;
        }
        bd = bd.setScale(2, RoundingMode.DOWN).stripTrailingZeros();
        return bd.toPlainString() + SUFFIX[i];
    }

    public static Component plain(String s, TextColor c) {
        return Component.text(smallCaps(s), c).decoration(TextDecoration.ITALIC, false);
    }

    /** Full number with thousands separators, max 2 decimals (rounded down): 1221500.5 -> 1,221,500.5 */
    public static String fullMoney(double v) {
        DecimalFormat df = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        df.setRoundingMode(RoundingMode.DOWN);
        return df.format(BigDecimal.valueOf(v));
    }

    /** +$<amount sold> : "$" green, everything else white, amount in full form (no K/M) */
    public static Component sold(double amount) {
        return Component.text("+", NamedTextColor.WHITE)
                .append(Component.text("$", NamedTextColor.GREEN))
                .append(Component.text(fullMoney(amount), NamedTextColor.WHITE));
    }

    /** <Item> is unsellable : item #00a2ff, unsellable red */
    public static Component unsellable(Material m) {
        return Component.text(itemName(m), BLUE)
                .append(Component.text(" is unsellable", NamedTextColor.RED));
    }

    /** <Item> is worth $<price> */
    public static Component worth(Material m, double price) {
        return Component.text(itemName(m), BLUE)
                .append(Component.text(" is worth ", NamedTextColor.WHITE))
                .append(Component.text("$", NamedTextColor.GREEN))
                .append(Component.text(money(price), BLUE));
    }

    /** "Key: " in #00a2ff followed by a value component (no italics). */
    public static Component kv(String key, Component value) {
        return Component.text(key + ": ", BLUE).append(value).decoration(TextDecoration.ITALIC, false);
    }

    /** "$" green + amount white */
    public static Component dollar(String amount) {
        return Component.text("$", NamedTextColor.GREEN).append(Component.text(amount, NamedTextColor.WHITE));
    }

    public static Component gray(String s) {
        return Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    public static String number(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    public static void sendBoth(Player p, Component c) {
        p.sendMessage(c);
        p.sendActionBar(c);
    }
}
