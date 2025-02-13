package net.earthcomputer.multiconnect.packets.v1_16_1;

import net.earthcomputer.multiconnect.ap.MessageVariant;
import net.earthcomputer.multiconnect.ap.NetworkEnum;
import net.earthcomputer.multiconnect.ap.Polymorphic;
import net.earthcomputer.multiconnect.api.Protocols;
import net.earthcomputer.multiconnect.packets.CPacketRecipeBookSeenRecipe;
import net.minecraft.resources.ResourceLocation;

@MessageVariant(minVersion = Protocols.V1_14, maxVersion = Protocols.V1_16_1)
@Polymorphic
public abstract class CPacketRecipeBookSeenRecipe_1_16_1 implements CPacketRecipeBookSeenRecipe {
    public Mode mode;

    @MessageVariant(minVersion = Protocols.V1_14, maxVersion = Protocols.V1_16_1)
    @Polymorphic(stringValue = "SHOWN")
    public static class Shown extends CPacketRecipeBookSeenRecipe_1_16_1 implements CPacketRecipeBookSeenRecipe.Shown {
        public ResourceLocation recipeId;
    }

    @MessageVariant(minVersion = Protocols.V1_14, maxVersion = Protocols.V1_16_1)
    @Polymorphic(stringValue = "SETTINGS")
    public static class Settings extends CPacketRecipeBookSeenRecipe_1_16_1 implements CPacketRecipeBookSeenRecipe.Settings {
        public boolean guiOpen;
        public boolean filteringCraftable;
        public boolean furnaceGuiOpen;
        public boolean furnaceFilteringCraftable;
        public boolean blastFurnaceGuiOpen;
        public boolean blastFurnaceFilteringCraftable;
        public boolean smokerGuiOpen;
        public boolean smokerFilteringCraftable;
    }

    @NetworkEnum
    public enum Mode {
        SHOWN, SETTINGS
    }
}
