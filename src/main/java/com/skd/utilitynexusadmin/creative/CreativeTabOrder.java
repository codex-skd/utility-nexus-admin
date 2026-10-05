package com.skd.utilitynexusadmin.creative;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Client-side ordering of the creative inventory tabs.
 *
 * <p>NeoForge hands the creative screen the tab order in
 * {@link CreativeModeTabRegistry#getSortedCreativeModeTabs()}, so reordering that
 * list is enough to sort the whole tab bar. Sorting by the display name (instead
 * of by tab id) keeps the result meaningful for the player: the same pack in
 * Spanish shows "Armas" next to "Bloques" instead of next to the ids.
 */
@OnlyIn(Dist.CLIENT)
public final class CreativeTabOrder {

    private CreativeTabOrder() {
    }

    /**
     * Returns a new list with the tabs in their final order.
     *
     * @param tabs           the tab list as NeoForge ordered it
     * @param vanillaFirst   {@code true} to keep the vanilla tabs at the front in
     *                       NeoForge's own order and sort only the mod tabs
     *                       alphabetically; {@code false} to sort every tab,
     *                       vanilla ones included, alphabetically
     */
    public static List<CreativeModeTab> sort(List<CreativeModeTab> tabs, boolean vanillaFirst) {
        if (!vanillaFirst) {
            return byDisplayName(tabs);
        }

        List<CreativeModeTab> vanilla = new ArrayList<>();
        List<CreativeModeTab> modded = new ArrayList<>();
        for (CreativeModeTab tab : tabs) {
            (isVanilla(tab) ? vanilla : modded).add(tab);
        }
        // Splitting the incoming list (instead of sorting it and then moving the
        // vanilla tabs up) is what keeps their original relative order.
        vanilla.addAll(byDisplayName(modded));
        return vanilla;
    }

    /**
     * Returns a new list sorted alphabetically by display name, using the collation
     * rules of the player's language.
     */
    private static List<CreativeModeTab> byDisplayName(List<CreativeModeTab> tabs) {
        Map<CreativeModeTab, String> displayNames = new HashMap<>();
        for (CreativeModeTab tab : tabs) {
            displayNames.put(tab, tab.getDisplayName().getString());
        }

        Collator collator = Collator.getInstance(playerLocale());
        // SECONDARY strength ignores case but keeps accents, so in Spanish "Añadir"
        // sorts with the other A entries instead of landing at the end of the list.
        collator.setStrength(Collator.SECONDARY);

        List<CreativeModeTab> sorted = new ArrayList<>(tabs);
        sorted.sort((first, second) -> {
            int byDisplayName = collator.compare(displayNames.get(first), displayNames.get(second));
            // Two tabs can end up with the same display name (untranslated literal, or a
            // language file that reuses a label): fall back to the registry id so the
            // order stays deterministic across runs.
            return byDisplayName != 0 ? byDisplayName : tabId(first).compareTo(tabId(second));
        });
        return sorted;
    }

    /**
     * Language selected in the options screen. NeoForge's LanguageManager exposes the
     * matching {@link Locale}; before the language manager exists it falls back to the
     * JVM default.
     */
    private static Locale playerLocale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getLanguageManager() != null) {
            Locale locale = minecraft.getLanguageManager().getJavaLocale();
            if (locale != null) {
                return locale;
            }
        }
        return Locale.getDefault();
    }

    /**
     * Vanilla tabs are the ones registered in the {@code minecraft} namespace; mods
     * register theirs under their own namespace, so the namespace alone is enough to
     * tell them apart.
     */
    private static boolean isVanilla(CreativeModeTab tab) {
        ResourceLocation id = CreativeModeTabRegistry.getName(tab);
        return id != null && ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace());
    }

    private static String tabId(CreativeModeTab tab) {
        return Objects.toString(CreativeModeTabRegistry.getName(tab), "");
    }
}
