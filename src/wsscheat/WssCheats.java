package wsscheat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import wsscheat.client.ClientSetup;

/**
 * 奇妙商店物语 · 作弊菜单
 *
 * 纯客户端模组：界面由客户端绘制，所有改动都在内置服务端线程上
 * 直接调用整合包核心模组（wonder_shop_story_core）的公开 API 完成。
 *
 * 所有入口都做了异常保护，即使出错也只会打印日志，不会让游戏崩溃。
 */
@Mod(value = WssCheats.MODID, dist = Dist.CLIENT)
public final class WssCheats {

    public static final String MODID = "wsscheat";
    public static final String NAME = "奇妙作弊菜单";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("wsscheat");

    public WssCheats(IEventBus modBus) {
        try {
            modBus.addListener(ClientSetup::onRegisterKeys);
            NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
            NeoForge.EVENT_BUS.addListener(CheatServer::onServerTick);
            LOGGER.info("[{}] {} v{} 已加载，按 F8 打开菜单", MODID, NAME, VERSION);
        } catch (Throwable error) {
            LOGGER.error("[{}] 初始化失败，作弊菜单将不可用", MODID, error);
        }
    }
}
