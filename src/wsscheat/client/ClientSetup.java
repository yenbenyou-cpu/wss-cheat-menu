package wsscheat.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import wsscheat.WssCheats;

public final class ClientSetup {

    public static final KeyMapping OPEN = new KeyMapping(
            "key.wsscheat.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.wsscheat");

    private static boolean reportedFailure;

    private ClientSetup() {
    }

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        try {
            event.register(OPEN);
        } catch (Throwable error) {
            WssCheats.LOGGER.error("[wsscheat] 注册按键失败", error);
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.screen instanceof CheatScreen) {
                    mc.setScreen(null);
                } else if (mc.screen == null) {
                    mc.setScreen(new CheatScreen());
                }
            }
        } catch (Throwable error) {
            if (!reportedFailure) {
                reportedFailure = true;
                WssCheats.LOGGER.error("[wsscheat] 客户端每刻任务出错", error);
            }
        }
    }
}
