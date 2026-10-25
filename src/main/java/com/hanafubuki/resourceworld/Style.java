package com.hanafubuki.resourceworld;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Палитра сообщений из config.yml → style. Тексты пишутся смысловыми тегами:
 * &lt;accent&gt; &lt;text&gt; &lt;muted&gt; &lt;dim&gt; &lt;success&gt; &lt;error&gt; &lt;gold&gt; и &lt;symbol&gt;.
 * Стиль Hanafubuki: https://github.com/Hanafubuki-minecraft/hanafubuki-brand/blob/main/STYLE.md
 */
public final class Style {

    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("accent", "#F7A1C0");
        DEFAULTS.put("text", "#F4EEF6");
        DEFAULTS.put("muted", "#A99BB5");
        DEFAULTS.put("dim", "#6F6480");
        DEFAULTS.put("success", "#A8E6B5");
        DEFAULTS.put("error", "#FF8A9B");
        DEFAULTS.put("gold", "#FFCF6B");
    }

    private Style() {
    }

    public static TagResolver resolver(ConfigurationSection style) {
        TagResolver.Builder b = TagResolver.builder();
        for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
            String hex = style == null ? e.getValue() : style.getString(e.getKey(), e.getValue());
            TextColor color = TextColor.fromHexString(hex == null ? e.getValue() : hex.trim());
            if (color == null) color = TextColor.fromHexString(e.getValue());
            b.resolver(TagResolver.resolver(e.getKey(), Tag.styling(color)));
        }
        String symbol = style == null ? "❀" : style.getString("symbol", "❀");
        b.resolver(TagResolver.resolver("symbol", Tag.selfClosingInserting(Component.text(symbol))));
        return b.build();
    }
}
