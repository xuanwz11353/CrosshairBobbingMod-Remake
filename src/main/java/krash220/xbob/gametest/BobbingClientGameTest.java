package krash220.xbob.gametest;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

import krash220.xbob.MainMod;
import krash220.xbob.game.api.Config;
import krash220.xbob.game.api.DynamicCrosshairCompat;
import krash220.xbob.game.api.Logger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;

/**
 * In-game verification harness: makes sure the crosshair transform really runs on 26.1 (static GUI pose),
 * that view bobbing actually displaces the crosshair, that disabling the "bob" option disables it again,
 * and that the ModMenu config screen renders without crashing.
 *
 * <p>Not part of the released mod: run with
 * {@code -Dfabric.client.gametest.modid=xbob -Dfabric.client.gametest.test=BobbingClientGameTest}.
 */
public class BobbingClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        System.out.println("[xbob-gametest] runTest entered");
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            System.out.println("[xbob-gametest] world created");
            context.waitFor(mc -> mc.player != null && mc.level != null);
            context.waitTicks(40);

            // Look at the ground: the offset is a projection of the point in the middle of the screen,
            // so a nearby surface makes the bobbing displacement clearly measurable.
            context.getInput().lookAt(0.0f, 75.0f);
            context.runOnClient(mc -> mc.options.hideGui = false);
            context.waitTicks(20);
            System.out.println("[xbob-gametest] applied=" + MainMod.debugApplied + " offset=" + MainMod.debugOffsetX + "/" + MainMod.debugOffsetY);

            require(context, MainMod.debugApplied, "crosshair transform was never applied while standing still");

            Path idleShot = context.takeScreenshot("xbob-1-idle");
            Logger.info("idle screenshot: {} (offset {} / {}, angle {}, scale {})", idleShot, MainMod.debugOffsetX, MainMod.debugOffsetY, MainMod.debugAngle, MainMod.debugScale);

            float[] walking = sampleWalk(context, 40, "xbob-2-walking");

            require(context, Math.abs(walking[0]) > 1.0e-3f || Math.abs(walking[1]) > 1.0e-3f,
                    "crosshair did not move while walking (offset " + walking[0] + " / " + walking[1] + ")");
            require(context, Math.abs(walking[2]) > 1.0e-3f, "crosshair was not tilted while walking (angle " + walking[2] + ")");

            Logger.info("walking screenshot taken (max offset {} / {}, max angle {}, dynamic-crosshair present {}, fix-centered hook hits {})",
                    walking[0], walking[1], walking[2], DynamicCrosshairCompat.isPresent(), DynamicCrosshairCompat.getFixHookHits());

            Config.set("bob", false);
            context.waitTicks(5);

            float[] withoutBob = sampleWalk(context, 30, "xbob-4-walking-nobob");

            Config.set("bob", true);

            require(context, Math.abs(withoutBob[0]) < 1.0e-3f && Math.abs(withoutBob[1]) < 1.0e-3f,
                    "crosshair still moved with bobbing disabled (offset " + withoutBob[0] + " / " + withoutBob[1] + ")");

            Logger.info("bobbing disabled offsets: {} / {}", withoutBob[0], withoutBob[1]);

            // Config screen (used by ModMenu): must no longer double-blur the background.
            context.runOnClient(mc -> {
                try {
                    Constructor<?> constructor = Class.forName("krash220.xbob.game.api.Config$ConfigScreen").getDeclaredConstructor(Screen.class);

                    constructor.setAccessible(true);
                    mc.setScreen((Screen) constructor.newInstance((Screen) null));
                } catch (Exception e) {
                    throw new RuntimeException("failed to open the xbob config screen", e);
                }
            });
            context.waitTicks(10);

            Path configShot = context.takeScreenshot("xbob-3-config");
            Logger.info("config screen screenshot: {}", configShot);

            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(5);
        }
    }

    private static float[] sampleWalk(ClientGameTestContext context, int ticks, String screenshot) {
        TestInput input = context.getInput();
        float maxX = 0.0f;
        float maxY = 0.0f;
        float maxAngle = 0.0f;

        input.holdKey(options -> options.keyUp);
        try {
            for (int i = 0; i < ticks; i++) {
                context.waitTick();
                maxX = Math.max(maxX, Math.abs(MainMod.debugOffsetX));
                maxY = Math.max(maxY, Math.abs(MainMod.debugOffsetY));
                maxAngle = Math.max(maxAngle, Math.abs(MainMod.debugAngle));
            }

            // Take the screenshot while the key is still held, so the captured frame really shows the bob.
            Logger.info("instant offset before screenshot {}: {} / {}, angle {}", screenshot, MainMod.debugOffsetX, MainMod.debugOffsetY, MainMod.debugAngle);
            context.takeScreenshot(screenshot);
        } finally {
            input.releaseKey(options -> options.keyUp);
        }

        return new float[] {maxX, maxY, maxAngle};
    }

    private static void require(ClientGameTestContext context, boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("xbob game test failed: " + message);
        }
    }
}
