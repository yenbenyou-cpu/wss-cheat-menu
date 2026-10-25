package wsscheat.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;
import wsscheat.CheatServer;
import wsscheat.WssCheats;

/**
 * 图形化作弊菜单。
 */
public class CheatScreen extends Screen {

    private static final int W = 420;
    private static final int H = 248;
    private static final int ROW = 28;
    private static final String[] TABS = { "金钱", "解锁", "玩家", "世界" };
    private static final int[] MULTIPLIER_PRESETS = { 1, 2, 3, 5, 10, 20, 50, 100 };

    private static final int COL_BG = 0xF01A1A22;
    private static final int COL_TITLE = 0xFF262636;
    private static final int COL_ACCENT = 0xFFE9B44C;
    private static final int COL_BORDER = 0xFF3C3C52;
    private static final int COL_TAB_ON = 0xFF35354A;
    private static final int COL_TAB_BG = 0xFF14141B;
    private static final int COL_TEXT = 0xFFEDEDF0;
    private static final int COL_DIM = 0xFF9A9AA8;
    private static final int COL_GOLD = 0xFFFFD766;
    private static final int COL_BAD = 0xFFFF7A7A;

    private int px;
    private int py;
    private int contentY;
    private int tab;
    private long lastRefreshMillis;
    private boolean reportedFailure;

    private String amountText = "10000";
    private EditBox amountBox;
    private Button multiplierButton;
    private Button autoButton;
    private Button nightVisionButton;
    private Button flyButton;
    private Button invulnerableButton;

    public CheatScreen() {
        super(Component.literal(WssCheats.NAME));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        try {
            px = (this.width - W) / 2;
            py = (this.height - H) / 2;
            contentY = py + 58;
            rebuild();
            CheatBridge.refresh();
        } catch (Throwable error) {
            reportOnce("菜单初始化出错", error);
            CheatBridge.status = "菜单初始化出错：" + error;
        }
    }

    private void rebuild() {
        this.clearWidgets();
        multiplierButton = null;
        autoButton = null;
        nightVisionButton = null;
        flyButton = null;
        invulnerableButton = null;
        amountBox = null;
        switch (tab) {
            case 0 -> buildMoneyTab();
            case 1 -> buildUnlockTab();
            case 2 -> buildPlayerTab();
            default -> buildWorldTab();
        }
        refreshMessages();
    }

    // ------------------------------------------------------------------ 各页

    private void buildMoneyTab() {
        int y = contentY;
        multiplierButton = button(px + 110, y, 108, 20, "倍率 ×1", this::cycleMultiplier);

        y += ROW;
        moneyButton(px + 110, y, 68, "+1万", 10_000L);
        moneyButton(px + 182, y, 68, "+10万", 100_000L);
        moneyButton(px + 254, y, 68, "+100万", 1_000_000L);
        moneyButton(px + 326, y, 68, "+1000万", 10_000_000L);

        y += ROW;
        amountBox = new EditBox(this.font, px + 110, y, 120, 20, Component.literal("金额"));
        amountBox.setMaxLength(15);
        amountBox.setValue(amountText);
        amountBox.setFilter(text -> text.isEmpty() || text.matches("[0-9]{1,15}"));
        amountBox.setResponder(text -> amountText = text);
        this.addRenderableWidget(amountBox);
        button(px + 238, y, 68, 20, "增加", this::applyAdd);
        button(px + 310, y, 68, 20, "设为", this::applySet);

        y += ROW;
        autoButton = button(px + 110, y, 160, 20, "自动补钱：关", this::toggleAuto);
    }

    private void buildUnlockTab() {
        button(px + 16, contentY, W - 32, 20, "一键全解锁（材料 / 道具 / 菜品 / 顾客 / 研究）",
                () -> CheatBridge.run(CheatServer::grantAll));
        button(px + 16, contentY + ROW, 186, 20, "解锁 1 家分店",
                () -> CheatBridge.run(player -> CheatServer.unlockBranches(player, 1)));
        button(px + 210, contentY + ROW, 186, 20, "解锁 2 家分店",
                () -> CheatBridge.run(player -> CheatServer.unlockBranches(player, 2)));
    }

    private void buildPlayerTab() {
        nightVisionButton = button(px + 16, contentY, 186, 20, "夜视：关",
                () -> CheatBridge.run(CheatServer::toggleNightVision));
        flyButton = button(px + 210, contentY, 186, 20, "飞行：关",
                () -> CheatBridge.run(CheatServer::toggleFly));
        invulnerableButton = button(px + 16, contentY + ROW, 186, 20, "无敌：关",
                () -> CheatBridge.run(CheatServer::toggleInvulnerable));
        button(px + 210, contentY + ROW, 186, 20, "+10 经验等级",
                () -> CheatBridge.run(player -> CheatServer.addLevels(player, 10)));
        button(px + 16, contentY + ROW * 2, 186, 20, "满血 + 满饱食",
                () -> CheatBridge.run(CheatServer::heal));
        button(px + 210, contentY + ROW * 2, 186, 20, "清除所有状态效果",
                () -> CheatBridge.run(CheatServer::clearEffects));
    }

    private void buildWorldTab() {
        button(px + 16, contentY, 92, 20, "清晨", () -> setTime(1000L));
        button(px + 113, contentY, 92, 20, "正午", () -> setTime(6000L));
        button(px + 210, contentY, 92, 20, "黄昏", () -> setTime(12000L));
        button(px + 307, contentY, 92, 20, "午夜", () -> setTime(18000L));

        button(px + 16, contentY + ROW, 122, 20, "晴天", () -> setWeather(0));
        button(px + 143, contentY + ROW, 122, 20, "下雨", () -> setWeather(1));
        button(px + 270, contentY + ROW, 122, 20, "雷暴", () -> setWeather(2));

        button(px + 16, contentY + ROW * 2, 250, 20, "清除周围敌对生物（64 格）",
                () -> CheatBridge.run(CheatServer::clearMonsters));
    }

    // ------------------------------------------------------------------ 动作

    private void moneyButton(int x, int y, int width, String label, long amount) {
        button(x, y, width, 20, label, () -> CheatBridge.run(player -> CheatServer.addMoney(player, amount)));
    }

    private void setTime(long time) {
        CheatBridge.run(player -> CheatServer.setTime(player, time));
    }

    private void setWeather(int mode) {
        CheatBridge.run(player -> CheatServer.setWeather(player, mode));
    }

    private void applyAdd() {
        Long value = parseAmount();
        if (value != null) {
            CheatBridge.run(player -> CheatServer.addMoney(player, value));
        }
    }

    private void applySet() {
        Long value = parseAmount();
        if (value != null) {
            CheatBridge.run(player -> CheatServer.setMoney(player, value));
        }
    }

    private Long parseAmount() {
        String raw = amountBox == null ? amountText : amountBox.getValue();
        raw = raw.trim().replace(",", "");
        if (raw.isEmpty()) {
            CheatBridge.status = "请先在输入框里填写金额";
            return null;
        }
        try {
            long value = Long.parseLong(raw);
            if (value < 0L) {
                CheatBridge.status = "金额不能是负数";
                return null;
            }
            return value;
        } catch (NumberFormatException error) {
            CheatBridge.status = "金额格式不正确：" + raw;
            return null;
        }
    }

    private void cycleMultiplier() {
        UUID id = CheatBridge.playerId();
        if (id == null) {
            return;
        }
        int current = CheatServer.multiplierOf(id);
        int next = MULTIPLIER_PRESETS[0];
        for (int i = 0; i < MULTIPLIER_PRESETS.length; i++) {
            if (MULTIPLIER_PRESETS[i] == current) {
                next = MULTIPLIER_PRESETS[(i + 1) % MULTIPLIER_PRESETS.length];
                break;
            }
        }
        int value = next;
        CheatBridge.run(player -> CheatServer.setMultiplier(player, value));
        refreshMessages();
    }

    private void toggleAuto() {
        CheatBridge.run(CheatServer::toggleAutoTopUp);
        refreshMessages();
    }

    // ------------------------------------------------------------------ 渲染

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            maybeRefresh();
            this.renderBackground(graphics, mouseX, mouseY, partialTick);
            drawPanel(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            drawTexts(graphics);
        } catch (Throwable error) {
            reportOnce("菜单渲染出错，已关闭界面", error);
            CheatBridge.status = "菜单渲染出错：" + error;
            this.onClose();
        }
    }

    private void drawPanel(GuiGraphics graphics) {
        graphics.fill(px, py, px + W, py + H, COL_BG);
        graphics.fill(px, py, px + W, py + 26, COL_TITLE);
        graphics.fill(px, py + 26, px + W, py + 27, COL_ACCENT);
        graphics.fill(px + 10, py + 29, px + W - 10, py + 51, COL_TAB_BG);
        graphics.fill(px + 10, py + H - 27, px + W - 10, py + H - 7, COL_TAB_BG);

        int tabWidth = (W - 20) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int x = px + 10 + i * tabWidth;
            if (i == tab) {
                graphics.fill(x, py + 29, x + tabWidth, py + 51, COL_TAB_ON);
                graphics.fill(x, py + 49, x + tabWidth, py + 51, COL_ACCENT);
            }
        }

        graphics.fill(px, py, px + 1, py + H, COL_BORDER);
        graphics.fill(px + W - 1, py, px + W, py + H, COL_BORDER);
        graphics.fill(px, py + H - 1, px + W, py + H, COL_BORDER);
    }

    private void drawTexts(GuiGraphics graphics) {
        graphics.drawString(this.font, WssCheats.NAME, px + 12, py + 9, COL_GOLD, false);

        String balance = CheatBridge.known
                ? "余额 " + CheatBridge.money(CheatBridge.balance)
                : "余额 读取中…";
        graphics.drawString(this.font, balance, px + W - 12 - this.font.width(balance), py + 9, COL_TEXT, false);

        int tabWidth = (W - 20) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            String label = TABS[i];
            int x = px + 10 + i * tabWidth + (tabWidth - this.font.width(label)) / 2;
            graphics.drawString(this.font, label, x, py + 36, i == tab ? COL_GOLD : COL_DIM, false);
        }

        int labelX = px + 16;
        if (tab == 0) {
            rowLabel(graphics, "金钱倍率", contentY);
            rowLabel(graphics, "快速加钱", contentY + ROW);
            rowLabel(graphics, "自定义金额", contentY + ROW * 2);
            rowLabel(graphics, "自动补钱", contentY + ROW * 3);
            graphics.drawString(this.font, "收益 ×N 自动补差", px + 226, contentY + 6, COL_DIM, false);
            graphics.drawString(this.font, "低于 5 万补到 500 万", px + 278, contentY + ROW * 3 + 6, COL_DIM, false);
        } else if (tab == 1) {
            graphics.drawString(this.font, "会一次性发放材料 / 道具，并额外 +100,000 金币。",
                    labelX, contentY + ROW * 2, COL_DIM, false);
            graphics.drawString(this.font, "需要先拥有自己的店铺，否则会提示失败。",
                    labelX, contentY + ROW * 2 + 12, COL_DIM, false);
        } else if (tab == 2) {
            graphics.drawString(this.font, "夜视 / 飞行 / 无敌 的状态取自当前玩家。",
                    labelX, contentY + ROW * 3, COL_DIM, false);
        } else {
            graphics.drawString(this.font, "时间与天气作用于当前存档的主世界。",
                    labelX, contentY + ROW * 3, COL_DIM, false);
        }

        String status = CheatBridge.status;
        if (!CheatBridge.available()) {
            graphics.drawString(this.font, "仅支持单人游戏：当前没有本地集成服务器",
                    px + 14, py + H - 21, COL_BAD, false);
        } else if (status == null || status.isEmpty()) {
            graphics.drawString(this.font, "就绪", px + 14, py + H - 21, COL_DIM, false);
        } else {
            String hint = "[F8 关闭]";
            int maxWidth = W - 28 - this.font.width(hint) - 8;
            String text = status;
            if (this.font.width(text) > maxWidth) {
                text = this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("…"))) + "…";
            }
            graphics.drawString(this.font, text, px + 14, py + H - 21, COL_TEXT, false);
        }

        String hint = "[F8 关闭]";
        graphics.drawString(this.font, hint, px + W - 14 - this.font.width(hint), py + H - 21, COL_DIM, false);
    }

    private void rowLabel(GuiGraphics graphics, String text, int y) {
        graphics.drawString(this.font, text, px + 16, y + 6, COL_TEXT, false);
    }

    // ------------------------------------------------------------------ 交互

    private Button button(int x, int y, int width, int height, String label, Runnable action) {
        Button widget = Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(x, y, width, height)
                .build();
        this.addRenderableWidget(widget);
        return widget;
    }

    private void refreshMessages() {
        UUID id = CheatBridge.playerId();
        if (id != null) {
            if (multiplierButton != null) {
                multiplierButton.setMessage(Component.literal("倍率 ×" + CheatServer.multiplierOf(id)));
            }
            if (autoButton != null) {
                autoButton.setMessage(Component.literal("自动补钱：" + (CheatServer.autoOf(id) ? "开" : "关")));
            }
        }
        Minecraft mc = Minecraft.getInstance();
        Abilities abilities = mc.player == null ? null : mc.player.getAbilities();
        if (nightVisionButton != null && mc.player != null) {
            nightVisionButton.setMessage(
                    Component.literal("夜视：" + (mc.player.hasEffect(MobEffects.NIGHT_VISION) ? "开" : "关")));
        }
        if (flyButton != null && abilities != null) {
            flyButton.setMessage(Component.literal("飞行：" + (abilities.mayfly ? "开" : "关")));
        }
        if (invulnerableButton != null && abilities != null) {
            invulnerableButton.setMessage(Component.literal("无敌：" + (abilities.invulnerable ? "开" : "关")));
        }
    }

    @Override
    public void tick() {
        try {
            maybeRefresh();
        } catch (Throwable error) {
            reportOnce("菜单刷新出错", error);
        }
    }

    /** 每 0.5 秒刷新一次余额与按钮文字（tick 与 render 都会调用，节流后不会重复排队）。 */
    private void maybeRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMillis < 500L) {
            return;
        }
        lastRefreshMillis = now;
        CheatBridge.refresh();
        refreshMessages();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int tabWidth = (W - 20) / TABS.length;
            for (int i = 0; i < TABS.length; i++) {
                int x = px + 10 + i * tabWidth;
                if (mouseX >= x && mouseX < x + tabWidth && mouseY >= py + 29 && mouseY < py + 51) {
                    if (tab != i) {
                        tab = i;
                        rebuild();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    private void reportOnce(String what, Throwable error) {
        if (reportedFailure) {
            return;
        }
        reportedFailure = true;
        WssCheats.LOGGER.error("[wsscheat] " + what, error);
    }
}
