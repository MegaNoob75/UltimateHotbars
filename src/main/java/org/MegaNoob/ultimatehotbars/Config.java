package org.MegaNoob.ultimatehotbars;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent.Loading;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK =
            BUILDER.comment("Whether to log the dirt block on common setup")
                    .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER =
            BUILDER.comment("A magic number")
                    .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION =
            BUILDER.comment("What you want the introduction message to be for the magic number")
                    .define("magicNumberIntroduction", "The magic number is... ");

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS =
            BUILDER.comment("A list of items to log on common setup.")
                    .defineListAllowEmpty(
                            "items",
                            List.of("minecraft:iron_ingot"),
                            Config::validateItemName
                    );

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // These get populated in onLoad():
    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    private static boolean validateItemName(final Object obj) {
        if (!(obj instanceof String itemName)) return false;
        ResourceLocation loc = ResourceLocation.tryParse(itemName);
        return loc != null && ForgeRegistries.ITEMS.containsKey(loc);
    }

    @SubscribeEvent
    static void onLoad(final Loading event) {
        logDirtBlock             = LOG_DIRT_BLOCK.get();
        magicNumber              = MAGIC_NUMBER.get();
        magicNumberIntroduction  = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream()
                .map(ResourceLocation::tryParse)      // parse safely
                .filter(Objects::nonNull)             // drop bad entries
                .map(ForgeRegistries.ITEMS::getValue)
                .filter(Objects::nonNull)             // drop missing items
                .collect(Collectors.toSet());
    }
}
