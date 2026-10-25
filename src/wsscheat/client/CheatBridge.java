package wsscheat.client;

import java.util.UUID;
import java.util.function.Function;

import org.merlin204.wss.shop.EmploymentData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端与服务端之间的桥：单人游戏时两者在同一进程，
 * 因此直接切到服务端线程调用核心模组的 API 即可，无需自定义数据包。
 */
public final class CheatBridge {

    /** 最近一次读到的余额 */
    public static volatile long balance;
    /** 是否成功读到过余额 */
    public static volatile boolean known;
    /** 最近一次操作结果，显示在菜单底部 */
    public static volatile String status = "";

    private CheatBridge() {
    }

    public static IntegratedServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    public static boolean available() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && server() != null;
    }

    public static UUID playerId() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getUUID();
    }

    /** 把一个服务端动作排到集成服务器线程执行。 */
    public static void run(Function<ServerPlayer, String> action) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = server();
        UUID id = playerId();
        if (server == null || id == null) {
            status = "仅支持单人游戏：当前没有本地集成服务器";
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                status = "服务端找不到当前玩家";
                return;
            }
            try {
                String result = action.apply(player);
                if (result != null) {
                    status = result;
                }
            } catch (Throwable error) {
                status = "出错：" + error;
            }
            read(id, server);
        });
    }

    /** 请求刷新余额显示。 */
    public static void refresh() {
        IntegratedServer server = server();
        UUID id = playerId();
        if (server == null || id == null) {
            known = false;
            return;
        }
        server.execute(() -> read(id, server));
    }

    private static void read(UUID id, IntegratedServer server) {
        try {
            balance = EmploymentData.get(server).balance(id);
            known = true;
        } catch (Throwable error) {
            known = false;
        }
    }

    public static String money(long value) {
        return String.format("%,d", value);
    }
}
