package net.earthcomputer.multiconnect.packets.latest;

import net.earthcomputer.multiconnect.ap.Introduce;
import net.earthcomputer.multiconnect.ap.MessageVariant;
import net.earthcomputer.multiconnect.api.Protocols;
import net.earthcomputer.multiconnect.packets.SPacketPlayerPosition;

@MessageVariant(minVersion = Protocols.V1_17)
public class SPacketPlayerPosition_Latest implements SPacketPlayerPosition {
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public byte flags;
    public int teleportId;
    @Introduce(booleanValue = false)
    public boolean dismountVehicle;
}
