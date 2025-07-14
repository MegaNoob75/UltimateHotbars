package org.MegaNoob.ultimatehotbars;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID)
public class CommandHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("ultimatehotbars")
                .then(Commands.literal("debuggui")
                        .then(Commands.literal("toggle")
                                .executes(ctx -> toggleDebug(ctx.getSource()))
                        )
                )
        );
    }

    private static int toggleDebug(CommandSourceStack source) {
        ultimatehotbars.DEBUG_MODE = !ultimatehotbars.DEBUG_MODE;

        source.sendSuccess(
                () -> Component.literal("UltimateHotbars debug GUI is now " +
                        (ultimatehotbars.DEBUG_MODE ? "§aENABLED" : "§cDISABLED")),
                false
        );

        return 1;
    }
}
