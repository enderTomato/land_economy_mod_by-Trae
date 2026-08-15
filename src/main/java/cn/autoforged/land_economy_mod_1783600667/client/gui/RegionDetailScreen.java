package cn.autoforged.land_economy_mod_1783600667.client.gui;

import cn.autoforged.land_economy_mod_1783600667.network.ModMessages;
import cn.autoforged.land_economy_mod_1783600667.network.PacketC2SPostMessage;
import cn.autoforged.land_economy_mod_1783600667.network.PacketS2CRegionDetail;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 区域详情面板（点击自己/他人已购买区块时弹出）。
 *
 * 显示：区域名称、所属玩家、GDP、人口、区域银行存款、成员列表、留言板。
 * 若是自己的区域且为成员，可发布留言。
 */
public class RegionDetailScreen extends Screen {

    private final PacketS2CRegionDetail data;
    private EditBox messageInput;
    private static final SimpleDateFormat FMT = new SimpleDateFormat("MM-dd HH:mm");

    private static final int W = 320, H = 220;

    public RegionDetailScreen(PacketS2CRegionDetail data) {
        super(Component.literal("区域详情"));
        this.data = data;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - W) / 2;
        int y = (height - H) / 2;

        // 留言输入框（仅自己区域）
        if (data.isMine()) {
            messageInput = new EditBox(Minecraft.getInstance().font,
                    x + 10, y + H - 50, W - 110, 16, Component.literal("留言"));
            messageInput.setMaxLength(256);
            addRenderableWidget(messageInput);

            addRenderableWidget(Button.builder(Component.literal("发布"), b -> postMessage())
                    .bounds(x + W - 90, y + H - 52, 80, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(x + W - 70, y + 8, 60, 16).build());
    }

    private void postMessage() {
        if (messageInput == null) return;
        String text = messageInput.getValue().trim();
        if (text.isEmpty()) return;
        ModMessages.sendToServer(new PacketC2SPostMessage(data.getRegionId(), text));
        messageInput.setValue("");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 半透明背景
        g.fill(0, 0, width, height, 0x80000000);

        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(x, y, x + W, y + H, 0xEE1A1A2E);
        g.renderOutline(x, y, W, H, 0xFFFFFFFF);

        var font = Minecraft.getInstance().font;
        int ty = y + 10;

        g.drawString(font, "§e=== " + data.getName() + " ===", x + 10, ty, 0xFFFFFFFF); ty += 14;
        g.drawString(font, "§7所属玩家: §f" + data.getOwnerName() + (data.isMine() ? " §a(自己)" : ""), x + 10, ty, 0xFFFFFFFF); ty += 12;
        g.drawString(font, "§7GDP: §a" + String.format("%,.2f", data.getGdp()), x + 10, ty, 0xFFFFFFFF); ty += 12;
        g.drawString(font, "§7人口: §b" + data.getPopulation(), x + 10, ty, 0xFFFFFFFF); ty += 12;
        g.drawString(font, "§7区域银行存款: §6" + String.format("%,.2f", data.getBank()), x + 10, ty, 0xFFFFFFFF); ty += 14;

        // 成员列表
        g.drawString(font, "§e=== 成员 ===", x + 10, ty, 0xFFFFFFFF); ty += 12;
        int memShown = 0;
        for (String name : data.getMembers()) {
            if (memShown >= 3) { g.drawString(font, "§7... 共 " + data.getMembers().size() + " 人", x + 10, ty, 0xFFFFFFFF); ty += 12; break; }
            g.drawString(font, "§f- " + name, x + 10, ty, 0xFFFFFFFF); ty += 11; memShown++;
        }
        if (data.getMembers().isEmpty()) { g.drawString(font, "§7(无)", x + 10, ty, 0xFFFFFFFF); ty += 11; }
        ty += 2;

        // 留言板
        g.drawString(font, "§e=== 留言板 ===", x + 10, ty, 0xFFFFFFFF); ty += 12;
        int msgShown = 0;
        for (var m : data.getMessages()) {
            if (msgShown >= 4) { g.drawString(font, "§7... 共 " + data.getMessages().size() + " 条", x + 10, ty, 0xFFFFFFFF); ty += 11; break; }
            String time = FMT.format(new Date(m.time()));
            String line = "§7[" + time + "] §f" + m.authorName() + ": §r" + m.text();
            g.drawString(font, line, x + 10, ty, 0xFFFFFFFF, false);
            ty += 11; msgShown++;
        }
        if (data.getMessages().isEmpty()) { g.drawString(font, "§7(暂无留言)", x + 10, ty, 0xFFFFFFFF); ty += 11; }

        // 留言输入框渲染
        if (messageInput != null) messageInput.render(g, mouseX, mouseY, partialTick);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
