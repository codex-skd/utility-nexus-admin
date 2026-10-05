package com.skd.utilitynexusadmin.mixin;

import com.skd.utilitynexusadmin.config.UNAConfig;
import com.skd.utilitynexusadmin.creative.CreativeTabOrder;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Sorts the creative inventory tabs alphabetically.
 *
 * <p>NeoForge computes the tab order once at registry freeze: the ten vanilla tabs
 * first, then every mod tab appended at the end unless it declared
 * {@code withTabsBefore}/{@code withTabsAfter} edges. That is a topological sort by
 * tab id, so the tab bar of a modded pack ends up grouped by mod rather than by name.
 * The creative screen reads that order from
 * {@link CreativeModeTabRegistry#getSortedCreativeModeTabs()}, so replacing the
 * returned list is enough. The list NeoForge returns is an unmodifiable view, hence
 * the copy in {@link CreativeTabOrder#alphabetically(List)}.
 *
 * <p>The hotbar, search, operator and inventory tabs are not part of that list —
 * NeoForge keeps them apart as "default tabs" and renders them in fixed columns —
 * so they are unaffected. Only the order of the tabs changes, not the items inside
 * each tab. Whether the vanilla tabs are sorted along with the mod tabs or kept at
 * the front in NeoForge's order is a config option.
 */
@Mixin(CreativeModeTabRegistry.class)
public class CreativeModeTabRegistryMixin {

    @Inject(method = "getSortedCreativeModeTabs", at = @At("RETURN"), cancellable = true)
    private static void utilityNexusAdmin$sortTabsAlphabetically(CallbackInfoReturnable<List<CreativeModeTab>> cir) {
        List<CreativeModeTab> tabs = cir.getReturnValue();
        if (tabs == null || tabs.size() < 2) {
            return;
        }
        try {
            if (UNAConfig.creativeSortTabsAlphabetically()) {
                cir.setReturnValue(CreativeTabOrder.sort(tabs, UNAConfig.creativeVanillaTabsFirst()));
            }
        } catch (IllegalStateException configNotLoadedYet) {
            // Same guard as UNALog: the COMMON config can still be unavailable this early
            // (first client setup pass), in which case NeoForge's own order is kept.
        }
    }
}
