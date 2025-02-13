package net.earthcomputer.multiconnect.packets.latest;

import net.earthcomputer.multiconnect.ap.MessageVariant;
import net.earthcomputer.multiconnect.ap.Registries;
import net.earthcomputer.multiconnect.ap.Registry;
import net.earthcomputer.multiconnect.api.Protocols;
import net.earthcomputer.multiconnect.packets.CPacketSetBeacon;

import java.util.OptionalInt;

@MessageVariant(minVersion = Protocols.V1_19)
public class CPacketSetBeacon_Latest implements CPacketSetBeacon {
    @Registry(Registries.MOB_EFFECT)
    public OptionalInt primaryEffect;
    @Registry(Registries.MOB_EFFECT)
    public OptionalInt secondaryEffect;
}
