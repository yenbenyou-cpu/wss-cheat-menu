package wsscheat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.merlin204.wss.shop.EmploymentData;
import org.merlin204.wss.shop.ShopCheats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.food.FoodData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 全部作弊逻辑都在服务端线程执行。
 */
public final class CheatServer {

    /** 会自动补到该余额的阈值（低于它就开始补） */
    public static final long AUTO_THRESHOLD = 50_000L;
    /** 自动补钱补到的目标余额 */
    public static final long AUTO_TARGET = 5_000_000L;

    private static final String REASON = "作弊菜单";
    private static final Map<UUID, Integer> MULTIPLIER = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> AUTO = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_BALANCE = new ConcurrentHashMap<>();
    /** 手动加钱后的静默期（tick），期间不做倍率补贴 */
    private static final Map<UUID, Integer> SUPPRESS = new ConcurrentHashMap<>();

    private CheatServer() {
    }

    // ------------------------------------------------------------------ 金钱

    public static long balance(ServerPlayer player) {
        return EmploymentData.get(player.server).balance(player.getUUID());
    }

    public static String addMoney(ServerPlayer player, long amount) {
        if (amount == 0L) {
            return "金额不能为 0";
        }
        EmploymentData data = EmploymentData.get(player.server);
        UUID id = player.getUUID();
        boolean ok = data.changeBalance(id, amount, REASON);
        long after = data.balance(id);
        if (!ok && after == balance(player) && amount > 0L) {
            return "加钱被拒绝（可能已达上限或资金被预留）";
        }
        touch(player, after);
        return "已增加 " + money(amount) + "　当前余额 " + money(after);
    }

    public static String setMoney(ServerPlayer player, long target) {
        EmploymentData data = EmploymentData.get(player.server);
        UUID id = player.getUUID();
        long before = data.balance(id);
        long delta = target - before;
        if (delta == 0L) {
            return "余额已经是 " + money(target);
        }
        boolean ok = data.changeBalance(id, delta, REASON + "·设定余额");
        long after = data.balance(id);
        if (!ok && after == before) {
            return "设定失败（扣钱时资金可能被预留）";
        }
        touch(player, after);
        return "余额已设为 " + money(after);
    }

    public static int multiplierOf(UUID id) {
        return MULTIPLIER.getOrDefault(id, 1);
    }

    public static int multiplier(ServerPlayer player) {
        return multiplierOf(player.getUUID());
    }

    public static String setMultiplier(ServerPlayer player, int value) {
        MULTIPLIER.put(player.getUUID(), Math.max(1, value));
        return "金钱倍率：×" + Math.max(1, value);
    }

    public static boolean autoOf(UUID id) {
        return AUTO.getOrDefault(id, Boolean.FALSE);
    }

    public static boolean autoTopUp(ServerPlayer player) {
        return autoOf(player.getUUID());
    }

    public static String toggleAutoTopUp(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean now = !AUTO.getOrDefault(id, Boolean.FALSE);
        AUTO.put(id, now);
        return now ? "自动补钱：开（低于 " + money(AUTO_THRESHOLD) + " 补到 " + money(AUTO_TARGET) + "）"
                : "自动补钱：关";
    }

    /** 手动改动余额后调用：同步快照，并暂时停用“收益倍率”，避免把作弊加的钱当成营业收入。 */
    private static void touch(ServerPlayer player, long balance) {
        UUID id = player.getUUID();
        LAST_BALANCE.put(id, balance);
        SUPPRESS.put(id, player.server.getTickCount() + 10);
    }

    // ------------------------------------------------------------------ 解锁

    public static String grantAll(ServerPlayer player) {
        try {
            String msg = ShopCheats.grant(player);
            return msg == null || msg.isEmpty() ? "一键解锁完成" : msg;
        } catch (Throwable t) {
            return "解锁失败：" + describe(t) + "（需要先创建并进入自己的店铺）";
        }
    }

    public static String unlockBranches(ServerPlayer player, int branches) {
        try {
            String msg = ShopCheats.unlockBranches(player, branches);
            return msg == null || msg.isEmpty() ? ("已解锁 " + branches + " 家分店") : msg;
        } catch (Throwable t) {
            return "解锁分店失败：" + describe(t);
        }
    }

    // ------------------------------------------------------------------ 玩家

    public static String heal(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        FoodData food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(20.0F);
        player.displayClientMessage(tag("生命与饱食度已回满"), true);
        return "生命与饱食度已回满";
    }

    public static String clearEffects(ServerPlayer player) {
        player.removeAllEffects();
        return "已清除全部状态效果";
    }

    public static String toggleNightVision(ServerPlayer player) {
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            return "夜视：关";
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        return "夜视：开";
    }

    public static String toggleFly(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        abilities.mayfly = !abilities.mayfly;
        if (!abilities.mayfly) {
            abilities.flying = false;
        }
        player.onUpdateAbilities();
        return "飞行：" + (abilities.mayfly ? "开（双击空格起飞）" : "关");
    }

    public static String toggleInvulnerable(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        abilities.invulnerable = !abilities.invulnerable;
        player.onUpdateAbilities();
        return "无敌：" + (abilities.invulnerable ? "开" : "关");
    }

    public static String addLevels(ServerPlayer player, int levels) {
        player.giveExperienceLevels(levels);
        return "已获得 " + levels + " 级经验";
    }

    // ------------------------------------------------------------------ 世界

    public static String setTime(ServerPlayer player, long time) {
        ServerLevel level = player.serverLevel();
        level.setDayTime(time);
        return "时间已设为 " + time + "（" + describeTime(time) + "）";
    }

    public static String setWeather(ServerPlayer player, int mode) {
        ServerLevel level = player.server.overworld();
        switch (mode) {
            case 0 -> level.setWeatherParameters(6000, 0, false, false);
            case 1 -> level.setWeatherParameters(0, 6000, true, false);
            default -> level.setWeatherParameters(0, 6000, true, true);
        }
        return "天气：" + (mode == 0 ? "晴" : mode == 1 ? "雨" : "雷暴");
    }

    public static String clearMonsters(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int removed = 0;
        for (Monster mob : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(64.0D))) {
            mob.discard();
            removed++;
        }
        return "清除了 " + removed + " 只敌对生物（半径 64 格）";
    }

    // ------------------------------------------------------------------ 每刻任务

    public static void onServerTick(ServerTickEvent.Post event) {
        try {
            tick(event);
        } catch (Throwable error) {
            if (!reportedFailure) {
                reportedFailure = true;
                WssCheats.LOGGER.error("[wsscheat] 服务端每刻任务出错（已自动停用倍率/补钱）", error);
            }
        }
    }

    private static boolean reportedFailure;

    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 5 != 0) {
            return;
        }
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        EmploymentData data = EmploymentData.get(server);
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            long current = data.balance(id);
            Long previous = LAST_BALANCE.put(id, current);

            // 自动补钱
            if (AUTO.getOrDefault(id, Boolean.FALSE) && current < AUTO_THRESHOLD) {
                long add = AUTO_TARGET - current;
                if (add > 0L && data.changeBalance(id, add, REASON + "·自动补钱")) {
                    LAST_BALANCE.put(id, data.balance(id));
                    continue;
                }
            }

            // 收益倍率：把这一段时间的净收入按倍率补上差额
            int mult = MULTIPLIER.getOrDefault(id, 1);
            boolean suppressed = SUPPRESS.getOrDefault(id, -1) > server.getTickCount();
            if (previous != null && mult > 1 && !suppressed) {
                long gained = current - previous;
                if (gained > 0L) {
                    long extra = safeMultiply(gained, mult - 1);
                    if (extra > 0L && data.changeBalance(id, extra, REASON + "·倍率补贴")) {
                        LAST_BALANCE.put(id, data.balance(id));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 工具

    private static long safeMultiply(long value, int factor) {
        try {
            return Math.multiplyExact(value, (long) factor);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE / 8L;
        }
    }

    private static String describe(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = t.getClass().getSimpleName();
        }
        return msg;
    }

    private static Component tag(String text) {
        return Component.literal("[" + WssCheats.NAME + "] " + text).withStyle(ChatFormatting.AQUA);
    }

    public static String money(long value) {
        return String.format("%,d", value);
    }

    private static String describeTime(long time) {
        long day = time % 24000L;
        if (day < 1000L) return "清晨";
        if (day < 6000L) return "上午";
        if (day < 12000L) return "正午";
        if (day < 13000L) return "黄昏";
        if (day < 18000L) return "夜晚";
        return "深夜";
    }
}
