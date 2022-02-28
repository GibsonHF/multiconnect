package net.earthcomputer.multiconnect.impl;

import com.google.common.cache.Cache;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.*;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.EmptyByteBuf;
import net.earthcomputer.multiconnect.api.Protocols;
import net.earthcomputer.multiconnect.api.ThreadSafe;
import net.earthcomputer.multiconnect.api.IProtocol;
import net.earthcomputer.multiconnect.connect.ConnectionMode;
import net.earthcomputer.multiconnect.mixin.bridge.TrackedDataHandlerRegistryAccessor;
import net.earthcomputer.multiconnect.protocols.generic.*;
import net.earthcomputer.multiconnect.protocols.generic.blockconnections.BlockConnections;
import net.earthcomputer.multiconnect.protocols.v1_14_4.Protocol_1_14_4;
import net.earthcomputer.multiconnect.protocols.v1_17_1.Protocol_1_17_1;
import net.earthcomputer.multiconnect.transformer.InboundTranslator;
import net.earthcomputer.multiconnect.transformer.TransformerByteBuf;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.tag.TagKey;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.EightWayDirection;
import net.minecraft.util.registry.*;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Utils {
    private static final Logger LOGGER = LogManager.getLogger("multiconnect");

    public static NbtCompound datafix(DSL.TypeReference type, NbtCompound old) {
        return (NbtCompound) datafix(type, NbtOps.INSTANCE, old);
    }

    public static <T> T datafix(DSL.TypeReference type, DynamicOps<T> ops, T old) {
        int oldVersion = ConnectionMode.byValue(ConnectionInfo.protocolVersion).getDataVersion();
        int currentVersion = SharedConstants.getGameVersion().getWorldVersion();
        if (oldVersion == currentVersion) {
            return old;
        }
        DataFixer fixer = MinecraftClient.getInstance().getDataFixer();
        Dynamic<T> translated = fixer.update(type, new Dynamic<>(ops, old), oldVersion, currentVersion);
        return translated.getValue();
    }

    public static boolean isChunkEmpty(WorldChunk chunk) {
        if (chunk.isEmpty()) {
            return true;
        }
        for (ChunkSection section : chunk.getSectionArray()) {
            if (!section.isEmpty()) { // TODO: this was ChunkSection.isEmpty (static method), impl the same?
                return false;
            }
        }
        return true;
    }

    @SafeVarargs
    public static <T, U> Comparator<T> orderBy(Function<T, U> mapper, U... order) {
        var indexBuilder = ImmutableMap.<U, Integer>builder();
        for (int i = 0; i < order.length; i++) {
            indexBuilder.put(order[i], i);
        }
        ImmutableMap<U, Integer> indexes = indexBuilder.build();
        Integer absent = indexes.size();
        return Comparator.comparing(val -> indexes.getOrDefault(mapper.apply(val), absent));
    }

    public static void insertAfter(List<PacketInfo<?>> list, Class<? extends Packet<?>> element, PacketInfo<?> toInsert) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getPacketClass() == element) {
                list.add(i + 1, toInsert);
                return;
            }
        }
        list.add(0, toInsert);
    }

    public static void remove(List<PacketInfo<?>> list, Class<? extends Packet<?>> element) {
        list.removeIf(it -> it.getPacketClass() == element);
    }

    public static void removeTrackedDataHandler(TrackedDataHandler<?> handler) {
        var biMap = TrackedDataHandlerRegistryAccessor.getDataHandlers();
        //noinspection unchecked
        var iBiMap = (IInt2ObjectBiMap<TrackedDataHandler<?>>) biMap;
        int id = TrackedDataHandlerRegistry.getId(handler);
        iBiMap.multiconnect_remove(handler);
        for (; TrackedDataHandlerRegistry.get(id + 1) != null; id++) {
            TrackedDataHandler<?> h = TrackedDataHandlerRegistry.get(id + 1);
            iBiMap.multiconnect_remove(h);
            biMap.put(h, id);
        }
    }

    public static void copyBlocks(TagRegistry<Item> tags, TagRegistry<Block> blockTags, TagKey<Item> tag, TagKey<Block> blockTag) {
        tags.add(tag, Collections2.transform(blockTags.get(blockTag), Block::asItem));
    }

    public static <T> int getUnmodifiedId(Registry<T> registry, T value) {
        Registry<T> defaultRegistry = DefaultRegistries.getDefaultRegistry(registry.getKey());
        if (defaultRegistry == null) return registry.getRawId(value);
        return defaultRegistry.getRawId(value);
    }

    public static <T> Identifier getUnmodifiedName(Registry<T> registry, T value) {
        Registry<T> defaultRegistry = DefaultRegistries.getDefaultRegistry(registry.getKey());
        if (defaultRegistry == null) return registry.getId(value);
        return defaultRegistry.getId(value);
    }

    /**
     * Clones an object with its codec by serializing and deserializing it
     */
    @ThreadSafe
    public static <T> T clone(Codec<T> codec, T val) {
        DataResult<NbtElement> nbtDataResult = codec.encodeStart(NbtOps.INSTANCE, val);
        if (nbtDataResult.error().isPresent()) {
            LOGGER.info("Failed to encode for cloning");
            return val;
        }
        //noinspection OptionalGetWithoutIsPresent
        DataResult<T> cloneDataResult = codec.parse(NbtOps.INSTANCE, nbtDataResult.result().get());
        if (cloneDataResult.error().isPresent()) {
            LOGGER.info("Failed to decode for cloning");
            return val;
        }

        //noinspection OptionalGetWithoutIsPresent
        return cloneDataResult.result().get();
    }

    public static DropDownWidget<ConnectionMode> createVersionDropdown(Screen screen, ConnectionMode initialMode) {
        var versionDropDown = new DropDownWidget<>(screen.width - 80, 5, 75, 20, initialMode, mode -> {
            LiteralText text = new LiteralText(mode.getName());
            if (mode.isMulticonnectBeta()) {
                text.append(new LiteralText(" !").formatted(Formatting.RED));
            }
            return text;
        })
                .setCategoryLabelExtractor(mode -> {
                    LiteralText text = new LiteralText(mode.getMajorReleaseName());
                    if (mode.isMulticonnectBeta()) {
                        text.append(new LiteralText(" !").formatted(Formatting.RED));
                    }
                    return text;
                })
                .setTooltipRenderer((matrices, mode, x, y, isCategory) -> {
                    if (mode.isMulticonnectBeta()) {
                        String modeName = isCategory ? mode.getMajorReleaseName() : mode.getName();
                        screen.renderTooltip(matrices, ImmutableList.of(
                                new TranslatableText("multiconnect.betaWarning.line1", modeName),
                                new TranslatableText("multiconnect.betaWarning.line2", modeName)
                        ), x, y);
                    }
                });

        // populate the versions
        for (ConnectionMode mode : ConnectionMode.values()) {
            if (mode.isMajorRelease()) {
                var category = versionDropDown.add(mode);
                List<IProtocol> children = mode.getMinorReleases();
                if (children.size() > 1) {
                    for (IProtocol child : children) {
                        category.add((ConnectionMode) child);
                    }
                }
            }
        }

        return versionDropDown;
    }

    @ThreadSafe
    public static void leftShift(BitSet bitSet, int n) {
        if (n < 0) {
            rightShift(bitSet, -n);
        } else if (n > 0) {
            for (int i = bitSet.length(); (i = bitSet.previousSetBit(i - 1)) != -1;) {
                bitSet.set(i + n);
                bitSet.clear(i);
            }
        }
    }

    @ThreadSafe
    public static void rightShift(BitSet bitSet, int n) {
        if (n < 0) {
            leftShift(bitSet, -n);
        } else if (n > 0) {
            for (int i = bitSet.nextSetBit(n); i != -1; i = bitSet.nextSetBit(i + 1)) {
                bitSet.set(i - n);
                bitSet.clear(i);
            }
        }
    }

    @ThreadSafe
    public static <T extends Packet<?>> T createPacket(Class<T> packetClass, Function<PacketByteBuf, T> constructor, int protocolVersion, InboundTranslator<T> creator) {
        TypedMap userData = new TypedMap();
        TransformerByteBuf buf = new TransformerByteBuf(new EmptyByteBuf(ByteBufAllocator.DEFAULT), null)
                .readTopLevelType(packetClass, protocolVersion, creator, userData);
        T packet = constructor.apply(buf);
        if (packet instanceof IUserDataHolder holder) {
            holder.multiconnect_getUserData().putAll(userData);
        }
        return packet;
    }

    private static final ScheduledExecutorService AUTO_CACHE_CLEAN_EXECUTOR = Executors.newScheduledThreadPool(1);
    private static final Cleaner AUTO_CACHE_CLEANER = Cleaner.create();
    public static void autoCleanUp(Cache<?, ?> cache, long time, TimeUnit timeUnit) {
        WeakReference<Cache<?, ?>> weakCache = new WeakReference<>(cache);
        ScheduledFuture<?> autoCleanTask = AUTO_CACHE_CLEAN_EXECUTOR.scheduleAtFixedRate(() -> {
            Cache<?, ?> c = weakCache.get();
            if (c != null) {
                c.cleanUp();
            }
        }, time, time, timeUnit);
        AUTO_CACHE_CLEANER.register(cache, () -> autoCleanTask.cancel(false));
    }

    @ThreadSafe
    public static String toString(Object o) {
        if (o == null || !o.getClass().isArray()) {
            return String.valueOf(o);
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0, e = Array.getLength(o); i < e; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(toString(Array.get(o, i)));
        }

        return sb.append("]").toString();
    }

    @ThreadSafe
    public static String toString(Object o, int maxLen) {
        String str = toString(o);
        if (str.length() > maxLen && maxLen > "...".length()) {
            return str.substring(0, maxLen - "...".length()) + "...";
        }
        return str;
    }

    @ThreadSafe
    public static <T extends Comparable<T>> void heapify(List<T> list) {
        heapify(list, Comparator.naturalOrder());
    }

    @ThreadSafe
    public static <T> void heapify(List<T> list, Comparator<? super T> comparator) {
        // See: PriorityQueue.heapify
        int n = list.size();
        for (int i = (n >>> 1) - 1; i >= 0; i--) {
            heapSiftDown(list, i, comparator);
        }
    }

    @ThreadSafe
    public static <T extends Comparable<T>> void heapAdd(List<T> list, T element) {
        heapAdd(list, element, Comparator.naturalOrder());
    }

    @ThreadSafe
    public static <T> void heapAdd(List<T> list, T element, Comparator<? super T> comparator) {
        list.add(element);
        heapSiftUp(list, list.size() - 1, comparator);
    }

    @ThreadSafe
    public static <T extends Comparable<T>> T heapRemove(List<T> list) {
        return heapRemove(list, Comparator.naturalOrder());
    }

    @ThreadSafe
    public static <T> T heapRemove(List<T> list, Comparator<? super T> comparator) {
        if (list.size() <= 1) {
            return list.remove(0);
        }
        T result = list.set(0, list.remove(list.size() - 1));
        heapSiftDown(list, 0, comparator);
        return result;
    }

    @ThreadSafe
    private static <T> void heapSiftDown(List<T> list, int k, Comparator<? super T> comparator) {
        // See: PriorityQueue.siftDown
        int n = list.size();
        int half = n >>> 1;
        T x = list.get(k);
        while (k < half) {
            int child = (k << 1) + 1;
            T c = list.get(child);
            int right = child + 1;
            if (right < n && comparator.compare(c, list.get(right)) > 0) {
                child = right;
                c = list.get(child);
            }
            if (comparator.compare(x, c) <= 0) {
                break;
            }
            list.set(k, c);
            k = child;
        }
        list.set(k, x);
    }

    @ThreadSafe
    private static <T> void heapSiftUp(List<T> list, int k, Comparator<? super T> comparator) {
        // See: PriorityQueue.siftUp
        T x = list.get(k);
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            T e = list.get(parent);
            if (comparator.compare(x, e) >= 0) {
                break;
            }
            list.set(k, e);
            k = parent;
        }
        list.set(k, x);
    }

    @ThreadSafe
    @Contract("null -> fail")
    public static void checkConnectionValid(@Nullable ClientPlayNetworkHandler networkHandler) {
        if (networkHandler == null) {
            throw new ConnectionEndedException();
        }
    }

    public static ChunkDataS2CPacket createEmptyChunkDataPacket(int x, int z, World world, DynamicRegistryManager registryManager) {
        Registry<Biome> biomeRegistry = registryManager.get(Registry.BIOME_KEY);
        Biome plainsBiome = biomeRegistry.get(BiomeKeys.PLAINS);

        ChunkDataS2CPacket packet = new ChunkDataS2CPacket(new WorldChunk(world, new ChunkPos(x, z)), world.getLightingProvider(), new BitSet(), new BitSet(), true);
        //noinspection ConstantConditions
        IUserDataHolder iPacket = (IUserDataHolder) packet;
        iPacket.multiconnect_setUserData(ChunkDataTranslator.DATA_TRANSLATED_KEY, true);
        iPacket.multiconnect_setUserData(ChunkDataTranslator.DIMENSION_KEY, world.getDimension());
        iPacket.multiconnect_setUserData(BlockConnections.BLOCKS_NEEDING_UPDATE_KEY, new EnumMap<>(EightWayDirection.class));
        Biome[] biomes = new Biome[256];
        Arrays.fill(biomes, plainsBiome);
        iPacket.multiconnect_setUserData(Protocol_1_14_4.BIOME_DATA_KEY, biomes);
        if (ConnectionInfo.protocolVersion <= Protocols.V1_17_1) {
            iPacket.multiconnect_setUserData(Protocol_1_17_1.VERTICAL_STRIP_BITMASK, new BitSet());
        }
        return packet;
    }
}
