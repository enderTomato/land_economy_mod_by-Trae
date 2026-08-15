package cn.autoforged.land_economy_mod_1783600667.network;

import cn.autoforged.land_economy_mod_1783600667.client.ClientPacketReceivers;
import cn.autoforged.land_economy_mod_1783600667.data.RegionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 服务端下发区域详情（点击自己/他人区块时触发；发布留言后刷新）。
 */
public class PacketS2CRegionDetail {

    public record MessageDTO(UUID author, String authorName, String text, long time) {}

    private final UUID regionId;
    private final String name;
    private final String ownerName;
    private final List<String> members;
    private final double gdp;
    private final int population;
    private final double bank;
    private final List<MessageDTO> messages;
    private final boolean isMine;

    public PacketS2CRegionDetail(UUID regionId, String name, String ownerName, List<String> members,
                                 double gdp, int population, double bank,
                                 List<RegionData.MessageEntry> rawMessages, boolean isMine) {
        this.regionId = regionId;
        this.name = name;
        this.ownerName = ownerName;
        this.members = members;
        this.gdp = gdp;
        this.population = population;
        this.bank = bank;
        this.messages = new ArrayList<>();
        for (RegionData.MessageEntry m : rawMessages) {
            this.messages.add(new MessageDTO(m.author, m.authorName, m.text, m.time));
        }
        this.isMine = isMine;
    }

    public PacketS2CRegionDetail(UUID regionId, String name, String ownerName, List<String> members,
                                 double gdp, int population, double bank,
                                 List<MessageDTO> messages, boolean isMine, Object unused) {
        this.regionId = regionId;
        this.name = name;
        this.ownerName = ownerName;
        this.members = members;
        this.gdp = gdp;
        this.population = population;
        this.bank = bank;
        this.messages = messages;
        this.isMine = isMine;
    }

    public static void enc(PacketS2CRegionDetail m, FriendlyByteBuf b) {
        b.writeUUID(m.regionId);
        b.writeUtf(m.name);
        b.writeUtf(m.ownerName);
        b.writeVarInt(m.members.size());
        for (String s : m.members) b.writeUtf(s);
        b.writeDouble(m.gdp);
        b.writeInt(m.population);
        b.writeDouble(m.bank);
        b.writeVarInt(m.messages.size());
        for (MessageDTO msg : m.messages) {
            b.writeUUID(msg.author());
            b.writeUtf(msg.authorName());
            b.writeUtf(msg.text());
            b.writeLong(msg.time());
        }
        b.writeBoolean(m.isMine);
    }

    public static PacketS2CRegionDetail dec(FriendlyByteBuf b) {
        UUID regionId = b.readUUID();
        String name = b.readUtf();
        String ownerName = b.readUtf();
        int n = b.readVarInt();
        List<String> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) members.add(b.readUtf());
        double gdp = b.readDouble();
        int pop = b.readInt();
        double bank = b.readDouble();
        int m = b.readVarInt();
        List<MessageDTO> messages = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            messages.add(new MessageDTO(b.readUUID(), b.readUtf(), b.readUtf(), b.readLong()));
        }
        boolean isMine = b.readBoolean();
        return new PacketS2CRegionDetail(regionId, name, ownerName, members, gdp, pop, bank, messages, isMine, null);
    }

    public static void handle(PacketS2CRegionDetail m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketReceivers.onRegionDetail(m, ctx));
        ctx.get().setPacketHandled(true);
    }

    public UUID getRegionId() { return regionId; }
    public String getName() { return name; }
    public String getOwnerName() { return ownerName; }
    public List<String> getMembers() { return members; }
    public double getGdp() { return gdp; }
    public int getPopulation() { return population; }
    public double getBank() { return bank; }
    public List<MessageDTO> getMessages() { return messages; }
    public boolean isMine() { return isMine; }
}
