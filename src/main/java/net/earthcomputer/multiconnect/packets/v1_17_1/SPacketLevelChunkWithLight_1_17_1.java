package net.earthcomputer.multiconnect.packets.v1_17_1;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.earthcomputer.multiconnect.ap.Argument;
import net.earthcomputer.multiconnect.ap.DatafixTypes;
import net.earthcomputer.multiconnect.ap.Datafix;
import net.earthcomputer.multiconnect.ap.FilledArgument;
import net.earthcomputer.multiconnect.ap.Introduce;
import net.earthcomputer.multiconnect.ap.Length;
import net.earthcomputer.multiconnect.ap.MessageVariant;
import net.earthcomputer.multiconnect.ap.PartialHandler;
import net.earthcomputer.multiconnect.ap.Type;
import net.earthcomputer.multiconnect.ap.Types;
import net.earthcomputer.multiconnect.api.Protocols;
import net.earthcomputer.multiconnect.packets.ChunkData;
import net.earthcomputer.multiconnect.packets.SPacketLevelChunkWithLight;
import net.earthcomputer.multiconnect.protocols.generic.TypedMap;
import net.earthcomputer.multiconnect.protocols.v1_16.Protocol_1_16_5;
import net.earthcomputer.multiconnect.protocols.v1_17.Protocol_1_17_1;
import net.minecraft.nbt.CompoundTag;
import java.util.BitSet;
import java.util.List;

@MessageVariant(minVersion = Protocols.V1_17, maxVersion = Protocols.V1_17_1)
public class SPacketLevelChunkWithLight_1_17_1 implements SPacketLevelChunkWithLight {
    @Type(Types.INT)
    public int x;
    @Type(Types.INT)
    public int z;
    @Introduce(compute = "computeVerticalStripBitmask")
    public BitSet verticalStripBitmask;
    public CompoundTag heightmaps;
    @Introduce(compute = "computeBiomes")
    public IntList biomes;
    @Length(raw = true)
    public ChunkData data;
    @Datafix(DatafixTypes.BLOCK_ENTITY)
    public List<CompoundTag> blockEntities;

    public static BitSet computeVerticalStripBitmask(@Argument("verticalStripBitmask") int verticalStripBitmask) {
        return BitSet.valueOf(new long[] {verticalStripBitmask});
    }

    public static IntList computeBiomes(
            @Argument("fullChunk") boolean fullChunk,
            @Argument("biomes") IntList biomes
    ) {
        if (fullChunk) {
            return biomes;
        } else {
            // TODO: get the actual biome array from somewhere
            return new IntArrayList(new int[Protocol_1_16_5.BIOME_ARRAY_LENGTH]);
        }
    }

    @PartialHandler
    public static void saveFields(
            @Argument("verticalStripBitmask") BitSet verticalStripBitmask,
            @Argument("biomes") IntList biomes,
            @FilledArgument TypedMap userData
    ) {
        userData.put(Protocol_1_17_1.VERTICAL_STRIP_BITMASK, verticalStripBitmask);
        userData.put(Protocol_1_17_1.BIOMES, biomes);
    }
}
