package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoTotemOrder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private enum Stage {
        NONE, SHOP, SHOP_GEAR, SHOP_TOTEM, SHOP_CONFIRM, SHOP_CHECK_FULL, SHOP_EXIT,
        WAIT, TARGET_ORDERS, ORDERS, ORDERS_SELECT, ORDERS_CONFIRM, ORDERS_FINAL_EXIT, CYCLE_PAUSE
    }

    private Stage stage = Stage.NONE;
    private long stageStart = 0;

    private static final long WAIT_TIME_MS = 50;

    private int shulkerMoveIndex = 0;
    private long lastShulkerMoveTime = 0;
    private int finalExitCount = 0;
    private long finalExitStart = 0;

    // Targeting
    private String targetPlayer = "";
    private boolean isTargetingActive = false;

    // Settings
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Player Targeting");

    private final Setting<String> minPrice = sgGeneral.add(new StringSetting.Builder()
        .name("min-price")
        .description("Minimum price to accept orders.")
        .defaultValue("850")
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> speedMode = sgGeneral.add(new BoolSetting.Builder()
        .name("speed-mode")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableTargeting = sgTargeting.add(new BoolSetting.Builder()
        .name("enable-targeting")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> targetPlayerName = sgTargeting.add(new StringSetting.Builder()
        .name("target-player")
        .defaultValue("")
        .visible(enableTargeting::get)
        .build()
    );

    private final Setting<Boolean> targetOnlyMode = sgTargeting.add(new BoolSetting.Builder()
        .name("target-only-mode")
        .defaultValue(false)
        .visible(enableTargeting::get)
        .build()
    );

    private final Setting<List<String>> blacklistedPlayers = sgTargeting.add(new StringListSetting.Builder()
        .name("blacklisted-players")
        .defaultValue(List.of())
        .build()
    );

    public AutoTotemOrder() {
        super(Categories.Misc, "auto-totem-order", "Auto buys and completes totem orders.");
    }

    @Override
    public void onActivate() {
        updateTargetPlayer();

        stage = Stage.SHOP;
        stageStart = System.currentTimeMillis();
        shulkerMoveIndex = 0;
        lastShulkerMoveTime = 0;

        info("AutoTotemOrder started.");
    }

    @Override
    public void onDeactivate() {
        stage = Stage.NONE;
    }

    private void updateTargetPlayer() {
        targetPlayer = targetPlayerName.get().trim();
        isTargetingActive = enableTargeting.get() && !targetPlayer.isEmpty();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();

        switch (stage) {
            case SHOP -> {
                ChatUtils.sendPlayerMsg("/shop");
                stage = Stage.SHOP_GEAR;
                stageStart = now;
            }

            case SHOP_GEAR -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();

                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (isTotem(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_TOTEM;
                            stageStart = now;
                            return;
                        }
                    }
                }
            }

            case SHOP_TOTEM -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();

                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (isTotem(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_CONFIRM;
                            stageStart = now;
                            return;
                        }
                    }
                }
            }

            case SHOP_CONFIRM -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();

                    for (Slot slot : handler.slots) {
                        if (isConfirm(slot.getStack())) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.SHOP_EXIT;
                            stageStart = now;
                            return;
                        }
                    }
                }
            }

            case SHOP_EXIT -> {
                mc.player.closeHandledScreen();
                stage = Stage.WAIT;
                stageStart = now;
            }

            case WAIT -> {
                if (now - stageStart >= WAIT_TIME_MS) {
                    if (isTargetingActive) {
                        ChatUtils.sendPlayerMsg("/orders " + targetPlayer);
                    } else {
                        ChatUtils.sendPlayerMsg("/orders totem");
                    }
                    stage = Stage.ORDERS;
                    stageStart = now;
                }
            }

            case ORDERS -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();

                    for (Slot slot : handler.slots) {
                        ItemStack stack = slot.getStack();
                        if (isTotem(stack)) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.ORDERS_SELECT;
                            stageStart = now;
                            return;
                        }
                    }
                }
            }

            case ORDERS_SELECT -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();

                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (isTotem(stack)) {
                            for (Slot slot : handler.slots) {
                                if (slot.inventory == mc.player.getInventory() && slot.getIndex() == i) {
                                    mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                                }
                            }
                        }
                    }

                    stage = Stage.ORDERS_CONFIRM;
                    stageStart = now;
                }
            }

            case ORDERS_CONFIRM -> {
                if (mc.currentScreen instanceof GenericContainerScreen screen) {
                    ScreenHandler handler = screen.getScreenHandler();

                    for (Slot slot : handler.slots) {
                        if (isConfirm(slot.getStack())) {
                            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                            stage = Stage.ORDERS_FINAL_EXIT;
                            stageStart = now;
                            finalExitStart = now;
                            finalExitCount = 0;
                            return;
                        }
                    }
                }
            }

            case ORDERS_FINAL_EXIT -> {
                if (now - finalExitStart > 100) {
                    mc.player.closeHandledScreen();
                    finalExitCount++;

                    if (finalExitCount >= 2) {
                        stage = Stage.CYCLE_PAUSE;
                        stageStart = now;
                    } else {
                        finalExitStart = now;
                    }
                }
            }

            case CYCLE_PAUSE -> {
                if (now - stageStart > 50) {
                    stage = Stage.SHOP;
                    stageStart = now;
                }
            }
        }
    }

    private boolean isTotem(ItemStack stack) {
        return stack.getItem() == Items.TOTEM_OF_UNDYING;
    }

    private boolean isConfirm(ItemStack stack) {
        return stack.getItem() == Items.GREEN_STAINED_GLASS_PANE
            || stack.getItem() == Items.LIME_STAINED_GLASS_PANE;
    }
}
