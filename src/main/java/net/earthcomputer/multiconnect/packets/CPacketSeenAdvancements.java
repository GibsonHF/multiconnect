package net.earthcomputer.multiconnect.packets;

import net.earthcomputer.multiconnect.ap.MessageVariant;
import net.earthcomputer.multiconnect.ap.NetworkEnum;
import net.earthcomputer.multiconnect.ap.Polymorphic;
import net.minecraft.resources.ResourceLocation;

@MessageVariant
@Polymorphic
public abstract class CPacketSeenAdvancements {
    public Action action;

    @NetworkEnum
    public enum Action {
        OPENED, CLOSED
    }

    @Polymorphic(stringValue = "OPENED")
    @MessageVariant
    public static class Opened extends CPacketSeenAdvancements {
        public ResourceLocation tabId;
    }

    @Polymorphic(stringValue = "CLOSED")
    @MessageVariant
    public static class Closed extends CPacketSeenAdvancements {
    }
}
