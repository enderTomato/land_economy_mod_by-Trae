package cn.autoforged.land_economy_mod_1783600667.data;

import cn.autoforged.land_economy_mod_1783600667.LandEconomyMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

public class RegionData {

    // 11 -> 13：新增 region_fly(11)、block_update(12)
    public static final int TOTAL_PERMISSIONS = 13;

    private static final String[] PERMISSION_NAMES = {
            "explode", "undead_spawn", "phantom_spawn", "friendly_fire", "pvp",
            "explosion_block_damage", "container_access", "redstone_interact", "ender_pearl",
            "fire_spread", "block_place_break",
            "region_fly",   // 11：true=允许区域内成员飞行
            "block_update"  // 12：true=允许方块更新(默认)；false=区域冻结
    };

    private UUID regionId;
    private String name;
    private UUID owner;
    private BlockPos center;
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;
    private String dimensionId;
    private boolean[] permissions;
    private double gdp;
    private int population;
    private long lastGdpCalcTime;
    private long lastPopulationCalcTime;
    private int consecutiveGrowthChecks;
    private double bankDeposits;
    private double personalFunds;
    private Set<UUID> members;
    private UUID parentRegionId;
    private Set<UUID> childRegionIds;
    private boolean isFlyland;
    private Map<UUID, Long> pendingJoinRequests;
    // —— 地块系统：已购买区块集合（chunkKey = ChunkPos.asLong(x,z)）——
    private Set<Long> claimedChunks = new HashSet<>();
    // —— 留言板 ——
    private final List<MessageEntry> messages = new ArrayList<>();

    public RegionData() {
        this.regionId = UUID.randomUUID();
        this.permissions = new boolean[TOTAL_PERMISSIONS];
        for (int i = 0; i < TOTAL_PERMISSIONS; i++) {
            this.permissions[i] = true;
        }
        this.gdp = 0;
        this.population = 1;
        this.bankDeposits = 0;
        this.personalFunds = 0;
        this.dimensionId = "minecraft:overworld";
        this.members = new HashSet<>();
        this.childRegionIds = new HashSet<>();
        this.isFlyland = false;
        this.pendingJoinRequests = new HashMap<>();
    }

    /** 留言条目 */
    public static class MessageEntry {
        public final UUID author;
        public final String authorName;
        public final String text;
        public final long time;
        public MessageEntry(UUID author, String authorName, String text, long time) {
            this.author = author; this.authorName = authorName; this.text = text; this.time = time;
        }
        public CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Author", author);
            t.putString("AuthorName", authorName);
            t.putString("Text", text);
            t.putLong("Time", time);
            return t;
        }
        public static MessageEntry fromNbt(CompoundTag t) {
            return new MessageEntry(t.getUUID("Author"), t.getString("AuthorName"),
                    t.getString("Text"), t.getLong("Time"));
        }
    }

    // ====== 区块集合 API ======
    public static long chunkKey(BlockPos pos) { return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4); }
    public static long chunkKey(int cx, int cz) { return ChunkPos.asLong(cx, cz); }

    public Set<Long> getClaimedChunks() { return claimedChunks; }

    /** 新模式是否已启用（有任意已购买区块） */
    public boolean hasClaimedChunks() { return !claimedChunks.isEmpty(); }

    public boolean ownsChunk(long key) { return claimedChunks.contains(key); }
    public boolean ownsChunk(BlockPos pos) { return claimedChunks.contains(chunkKey(pos)); }
    public boolean addChunk(long key) { setDirty(); return claimedChunks.add(key); }
    public boolean removeChunk(long key) { setDirty(); return claimedChunks.remove(key); }

    /** 由 AABB 一次性写入其覆盖的全部区块（旧→新迁移/旧指令兼容） */
    public void addAllChunksInAABB() {
        if (minX == 0 && maxX == 0 && minZ == 0 && maxZ == 0) return;
        int cx0 = minX >> 4, cx1 = maxX >> 4, cz0 = minZ >> 4, cz1 = maxZ >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++)
                claimedChunks.add(chunkKey(cx, cz));
        setDirty();
    }

    /** 由当前 chunk 集合反算 AABB（新→旧显示兼容） */
    public void recomputeAABBFromChunks() {
        if (claimedChunks.isEmpty()) return;
        int mnX = Integer.MAX_VALUE, mnZ = Integer.MAX_VALUE, mxX = Integer.MIN_VALUE, mxZ = Integer.MIN_VALUE;
        for (long k : claimedChunks) {
            int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
            mnX = Math.min(mnX, cx << 4); mnZ = Math.min(mnZ, cz << 4);
            mxX = Math.max(mxX, (cx << 4) + 15); mxZ = Math.max(mxZ, (cz << 4) + 15);
        }
        this.minX = mnX; this.minZ = mnZ; this.maxX = mxX; this.maxZ = mxZ;
        if (center == null) center = new BlockPos((mnX + mxX) / 2, 64, (mnZ + mxZ) / 2);
        setDirty();
    }

    // ====== 留言板 ======
    public List<MessageEntry> getMessages() { return Collections.unmodifiableList(messages); }
    public void addMessage(UUID author, String authorName, String text, int max) {
        messages.add(new MessageEntry(author, authorName, text, System.currentTimeMillis()));
        while (messages.size() > max) messages.remove(0);
        setDirty();
    }

    /** 标记外部 SavedData 脏；RegionData 是 POJO，由 EconomySavedData.setDirty() 兜底 */
    private void setDirty() {
        EconomySavedData data = LandEconomyMod.getEconomyData();
        if (data != null) data.setDirty();
    }

    public RegionData(UUID owner, BlockPos center, int sizeX, int sizeZ) {
        this();
        this.owner = owner;
        this.center = center;
        this.minX = center.getX() - sizeX;
        this.minZ = center.getZ() - sizeZ;
        this.maxX = center.getX() + sizeX;
        this.maxZ = center.getZ() + sizeZ;
        this.name = owner.toString().substring(0, 8) + "'s Land";
    }

    public static RegionData fromNbt(CompoundTag tag) {
        RegionData data = new RegionData();
        data.regionId = tag.getUUID("RegionId");
        data.name = tag.getString("Name");
        if (tag.hasUUID("Owner")) {
            data.owner = tag.getUUID("Owner");
        }
        if (tag.contains("Center")) {
            data.center = NbtUtils.readBlockPos(tag.getCompound("Center"));
        }
        data.minX = tag.getInt("MinX");
        data.minZ = tag.getInt("MinZ");
        data.maxX = tag.getInt("MaxX");
        data.maxZ = tag.getInt("MaxZ");
        data.dimensionId = tag.getString("DimensionId");
        data.gdp = tag.getDouble("GDP");
        data.population = tag.getInt("Population");
        data.lastGdpCalcTime = tag.getLong("LastGDPCalcTime");
        data.lastPopulationCalcTime = tag.getLong("LastPopCalcTime");
        data.consecutiveGrowthChecks = tag.getInt("ConsecutiveGrowthChecks");
        data.bankDeposits = tag.getDouble("BankDeposits");
        data.personalFunds = tag.getDouble("PersonalFunds");

        if (tag.contains("Permissions")) {
            byte[] permBytes = tag.getByteArray("Permissions");
            for (int i = 0; i < Math.min(permBytes.length, TOTAL_PERMISSIONS); i++) {
                data.permissions[i] = permBytes[i] == 1;
            }
        }

        if (tag.contains("Members")) {
            ListTag membersList = tag.getList("Members", Tag.TAG_COMPOUND);
            for (int i = 0; i < membersList.size(); i++) {
                CompoundTag memberTag = membersList.getCompound(i);
                data.members.add(memberTag.getUUID("UUID"));
            }
        }

        if (tag.hasUUID("ParentRegionId")) {
            data.parentRegionId = tag.getUUID("ParentRegionId");
        }

        if (tag.contains("ChildRegionIds")) {
            ListTag childList = tag.getList("ChildRegionIds", Tag.TAG_COMPOUND);
            for (int i = 0; i < childList.size(); i++) {
                data.childRegionIds.add(childList.getCompound(i).getUUID("UUID"));
            }
        }

        data.isFlyland = tag.getBoolean("IsFlyland");

        if (tag.contains("PendingJoinRequests")) {
            ListTag joinList = tag.getList("PendingJoinRequests", Tag.TAG_COMPOUND);
            for (int i = 0; i < joinList.size(); i++) {
                CompoundTag entry = joinList.getCompound(i);
                data.pendingJoinRequests.put(entry.getUUID("Player"), entry.getLong("Time"));
            }
        }

        // —— 新增：chunk 集合 ——
        if (tag.contains("ClaimedChunks")) {
            long[] arr = tag.getLongArray("ClaimedChunks");
            for (long k : arr) data.claimedChunks.add(k);
        }
        // —— 新增：留言板 ——
        if (tag.contains("Messages")) {
            ListTag ml = tag.getList("Messages", Tag.TAG_COMPOUND);
            for (int i = 0; i < ml.size(); i++) data.messages.add(MessageEntry.fromNbt(ml.getCompound(i)));
        }

        return data;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("RegionId", regionId);
        tag.putString("Name", name);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (center != null) {
            tag.put("Center", NbtUtils.writeBlockPos(center));
        }
        tag.putInt("MinX", minX);
        tag.putInt("MinZ", minZ);
        tag.putInt("MaxX", maxX);
        tag.putInt("MaxZ", maxZ);
        tag.putString("DimensionId", dimensionId);
        tag.putDouble("GDP", gdp);
        tag.putInt("Population", population);
        tag.putLong("LastGDPCalcTime", lastGdpCalcTime);
        tag.putLong("LastPopCalcTime", lastPopulationCalcTime);
        tag.putInt("ConsecutiveGrowthChecks", consecutiveGrowthChecks);
        tag.putDouble("BankDeposits", bankDeposits);
        tag.putDouble("PersonalFunds", personalFunds);

        byte[] permBytes = new byte[TOTAL_PERMISSIONS];
        for (int i = 0; i < TOTAL_PERMISSIONS; i++) {
            permBytes[i] = (byte) (permissions[i] ? 1 : 0);
        }
        tag.putByteArray("Permissions", permBytes);

        ListTag membersList = new ListTag();
        for (UUID memberId : members) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("UUID", memberId);
            membersList.add(memberTag);
        }
        tag.put("Members", membersList);

        if (parentRegionId != null) {
            tag.putUUID("ParentRegionId", parentRegionId);
        }

        ListTag childList = new ListTag();
        for (UUID childId : childRegionIds) {
            CompoundTag childTag = new CompoundTag();
            childTag.putUUID("UUID", childId);
            childList.add(childTag);
        }
        tag.put("ChildRegionIds", childList);

        tag.putBoolean("IsFlyland", isFlyland);

        ListTag joinList = new ListTag();
        for (Map.Entry<UUID, Long> entry : pendingJoinRequests.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Player", entry.getKey());
            entryTag.putLong("Time", entry.getValue());
            joinList.add(entryTag);
        }
        tag.put("PendingJoinRequests", joinList);

        // —— 新增 ——
        tag.putLongArray("ClaimedChunks", claimedChunks.stream().mapToLong(Long::longValue).toArray());
        ListTag ml = new ListTag();
        for (MessageEntry m : messages) ml.add(m.toNbt());
        tag.put("Messages", ml);

        return tag;
    }

    // ====== containsPos：优先 chunk 集合，回退 AABB（旧数据） ======
    public boolean containsPos(BlockPos pos) {
        if (!claimedChunks.isEmpty()) return ownsChunk(pos);
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public boolean getPermission(int index) {
        if (index < 0 || index >= TOTAL_PERMISSIONS) return false;
        return permissions[index];
    }

    public void setPermission(int index, boolean value) {
        if (index >= 0 && index < TOTAL_PERMISSIONS) {
            permissions[index] = value;
        }
    }

    public static String getPermissionName(int index) {
        if (index >= 0 && index < PERMISSION_NAMES.length) {
            return PERMISSION_NAMES[index];
        }
        return "unknown_" + index;
    }

    public static int getPermissionIndex(String name) {
        for (int i = 0; i < PERMISSION_NAMES.length; i++) {
            if (PERMISSION_NAMES[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    public RegionType getRegionType() {
        return RegionType.getByGdpAndPopulation(gdp, population);
    }

    // Getters and Setters

    public UUID getRegionId() { return regionId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public BlockPos getCenter() { return center; }
    public void setCenter(BlockPos center) { this.center = center; }
    public int getMinX() { return minX; }
    public void setMinX(int minX) { this.minX = minX; }
    public int getMinZ() { return minZ; }
    public void setMinZ(int minZ) { this.minZ = minZ; }
    public int getMaxX() { return maxX; }
    public void setMaxX(int maxX) { this.maxX = maxX; }
    public int getMaxZ() { return maxZ; }
    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }
    public String getDimensionId() { return dimensionId; }
    public void setDimensionId(String dimensionId) { this.dimensionId = dimensionId; }
    public double getGdp() { return gdp; }
    public void setGdp(double gdp) { this.gdp = gdp; }
    public int getPopulation() { return population; }
    public void setPopulation(int population) { this.population = population; }
    public long getLastGdpCalcTime() { return lastGdpCalcTime; }
    public void setLastGdpCalcTime(long time) { this.lastGdpCalcTime = time; }
    public long getLastPopulationCalcTime() { return lastPopulationCalcTime; }
    public void setLastPopulationCalcTime(long time) { this.lastPopulationCalcTime = time; }
    public int getConsecutiveGrowthChecks() { return consecutiveGrowthChecks; }
    public void setConsecutiveGrowthChecks(int checks) { this.consecutiveGrowthChecks = checks; }
    public double getBankDeposits() { return bankDeposits; }
    public void setBankDeposits(double deposits) { this.bankDeposits = deposits; }
    public double getPersonalFunds() { return personalFunds; }
    public void setPersonalFunds(double funds) { this.personalFunds = funds; }
    public int getAreaSize() {
        if (!claimedChunks.isEmpty()) return claimedChunks.size() * 256;
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    public Set<UUID> getMembers() { return members; }

    public boolean isMember(UUID playerId) {
        return owner != null && (owner.equals(playerId) || members.contains(playerId));
    }

    public boolean addMember(UUID playerId) {
        if (members.contains(playerId)) return false;
        if (owner != null && owner.equals(playerId)) return false;
        return members.add(playerId);
    }

    public boolean removeMember(UUID playerId) {
        return members.remove(playerId);
    }

    // Hierarchy

    public UUID getParentRegionId() { return parentRegionId; }
    public void setParentRegionId(UUID parentRegionId) { this.parentRegionId = parentRegionId; }

    public Set<UUID> getChildRegionIds() { return childRegionIds; }

    public boolean isFlyland() { return isFlyland; }
    public void setFlyland(boolean flyland) { isFlyland = flyland; }

    public boolean isRootRegion() { return parentRegionId == null && !isFlyland; }

    // Overlap checking: returns true if this region overlaps with another
    public boolean overlapsWith(RegionData other) {
        if (!dimensionId.equals(other.dimensionId)) return false;
        return !(maxX < other.minX || minX > other.maxX || maxZ < other.minZ || minZ > other.maxZ);
    }

    // Pending join requests

    public Map<UUID, Long> getPendingJoinRequests() { return pendingJoinRequests; }

    public boolean hasPendingRequest(UUID playerId) {
        return pendingJoinRequests.containsKey(playerId);
    }

    public void addJoinRequest(UUID playerId) {
        pendingJoinRequests.put(playerId, System.currentTimeMillis());
    }

    public void removeJoinRequest(UUID playerId) {
        pendingJoinRequests.remove(playerId);
    }

    public boolean canRequestJoin(UUID playerId, long cooldownMs) {
        Long lastTime = pendingJoinRequests.get(playerId);
        if (lastTime == null) return true;
        return System.currentTimeMillis() - lastTime >= cooldownMs;
    }
}
