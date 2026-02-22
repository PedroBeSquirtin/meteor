package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotemOrder extends Module {

    private enum Stage {
        NONE,
        SHOP,
        SHOP_SELECT_TOTEM,
        SHOP_CONFIRM,
        SHOP_EXIT,
        WAIT,
        ORDERS_OPEN,
        ORDERS_FILL,
        ORDERS_CONFIRM,
        ORDERS_EXIT,
        LOOP_DELAY
    }

    private Stage stage = Stage.NONE;
    private long lastAction = 0;

    private static final long DELAY = 75;

    // SETTINGS
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> notifications = sgGeneral.add(
        new BoolSetting.Builder()
            .name("notifications")
            .defaultValue(true)
            .build()
    );

    public AutoTotemOrder() {
        super(Categories.Misc, "auto-totem-order", "Automatically buys totems and completes /orders.");
    }

    @Override
    public void onActivate() {
        stage = Stage.SHOP;
        lastAction = System.currentTimeMillis();
        info("Started AutoTotemOrder.");
    }

    @Override
    public void onDeactivate() {
        stage = Stage.NONE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        if (now - lastAction < DELAY) return;

        switch (stage) {

            case SHOP -> {
                ChatUtils.sendPlayerMsg("/shop");
                stage = Stage.SHOP_SELECT_TOTEM;
                lastAction = now;
            }

            case SHOP_SELECT_TOTEM -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

                ScreenHandler handler = screen.getScreenHandler();

                for (Slot slot : handler.slots) {
                    ItemStack stack = slot.getStack();

                    if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        stage = Stage.SHOP_CONFIRM;
                        lastAction = now;
                        return;
                    }
                }
            }

            case SHOP_CONFIRM -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

                ScreenHandler handler = screen.getScreenHandler();

                for (Slot slot : handler.slots) {
                    ItemStack stack = slot.getStack();

                    if (stack.getItem() == Items.LIME_STAINED_GLASS_PANE
                        || stack.getItem() == Items.GREEN_STAINED_GLASS_PANE) {

                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        stage = Stage.SHOP_EXIT;
                        lastAction = now;
                        return;
                    }
                }
            }

            case SHOP_EXIT -> {
                mc.player.closeHandledScreen();
                stage = Stage.WAIT;
                lastAction = now;
            }

            case WAIT -> {
                ChatUtils.sendPlayerMsg("/orders totem");
                stage = Stage.ORDERS_OPEN;
                lastAction = now;
            }

            case ORDERS_OPEN -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

                ScreenHandler handler = screen.getScreenHandler();

                for (Slot slot : handler.slots) {
                    ItemStack stack = slot.getStack();

                    if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        stage = Stage.ORDERS_FILL;
                        lastAction = now;
                        return;
                    }
                }
            }

            case ORDERS_FILL -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

                ScreenHandler handler = screen.getScreenHandler();

                for (int i = 0; i < mc.player.getInventory().size(); i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);

                    if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                        for (Slot slot : handler.slots) {
                            if (slot.inventory == mc.player.getInventory() && slot.getIndex() == i) {
                                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                            }
                        }
                    }
                }

                stage = Stage.ORDERS_CONFIRM;
                lastAction = now;
            }

            case ORDERS_CONFIRM -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

                ScreenHandler handler = screen.getScreenHandler();

                for (Slot slot : handler.slots) {
                    ItemStack stack = slot.getStack();

                    if (stack.getItem() == Items.LIME_STAINED_GLASS_PANE
                        || stack.getItem() == Items.GREEN_STAINED_GLASS_PANE) {

                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        stage = Stage.ORDERS_EXIT;
                        lastAction = now;
                        return;
                    }
                }
            }

            case ORDERS_EXIT -> {
                mc.player.closeHandledScreen();
                stage = Stage.LOOP_DELAY;
                lastAction = now;
            }

            case LOOP_DELAY -> {
                stage = Stage.SHOP;
                lastAction = now;
            }
        }
    }
}
