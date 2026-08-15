package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.client.ClientPacketReceivers;
import cn.autoforged.land_economy_mod_1783600667.data.EconomySavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端下发某区块范围（cx0..cx1, cz0..cz1）的区块归属快照。
 * 客户端写入 ChunkClaimCache 用于绘制区块颜色。
 */
public class PacketS2CChunkData {

    public record CellDTO(long chunkKey, UUID owner, String regionName, boolean isFlyland) {}

    private final List<CellDTO> cells;
    private final int cx0, cz0, cx1, cz1;

    public PacketS2CChunkData(Collection<EconomySavedData.ChunkCell> rawCells, int cx0, int cz0, int cx1, int cz1) {
        this.cells = new ArrayList<>(rawCells.size());
        for (EconomySavedData.ChunkCell c : rawCells) {
            this.cells.add(new CellDTO(c.chunkKey(), c.owner(), c.regionName(), c.isFlyland()));
        }
        this.cx0 = cx0; this.cz0 = cz0; this.cx1 = cx1; this.cz1 = cz1;
    }

    public PacketS2CChunkData(List<CellDTO> cells, int cx0, int cz0, int cx1, int cz1) {
        this.cells = cells; this.cx0 = cx0; this.cz0 = cz0; this.cx1 = cx1; this.cz1 = cz1;
    }

    public static void enc(PacketS2CChunkData m, FriendlyByteBuf b) {
        b.writeVarInt(m.cells.size());
        for (CellDTO c : m.cells) {
            b.writeLong(c.chunkKey());
            if (c.owner() != null) { b.writeBoolean(true); b.writeUUID(c.owner()); }
            else b.writeBoolean(false);
            b.writeUtf(c.regionName());
            b.writeBoolean(c.isFlyland());
        }
        b.writeInt(m.cx0); b.writeInt(m.cz0); b.writeInt(m.cx1); b.writeInt(m.cz1);
    }

    public static PacketS2CChunkData dec(FriendlyByteBuf b) {
        int n = b.readVarInt();
        List<CellDTO> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long k = b.readLong();
            UUID o = b.readBoolean() ? b.readUUID() : null;
            String name = b.readUtf();
            boolean fl = b.readBoolean();
            list.add(new CellDTO(k, o, name, fl));
        }
        int cx0 = b.readInt(), cz0 = b.readInt(), cx1 = b.readInt(), cz1 = b.readInt();
        return new PacketS2CChunkData(list, cx0, cz0, cx1, cz1);
    }

    public static void handle(PacketS2CChunkData m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketReceivers.onChunkData(m, ctx));
        ctx.get().setPacketHandled(true);
    }

    public List<CellDTO> getCells() { return cells; }
    public int getCx0() { return cx0; }
    public int getCz0() { return cz0; }
    public int getCx1() { return cx1; }
    public int getCz1() { return cz1; }
}