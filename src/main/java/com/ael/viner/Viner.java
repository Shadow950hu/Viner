package com.ael.viner;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import javax.swing.text.JTextComponent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Viner.MOD_ID)
public class Viner {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "viner";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // Create a map to store blockName to Block mappings
    static Map<String, Block> blockMap = new HashMap<>();

    // create vineable blocks set from configurable json
    static Set<Block> VINEABLE_BLOCKS = new HashSet<>();
    // default config for vineable blocks
    private static final String DEFAULT_CONFIG_VINEABLE = """
            {
              "vineable_limit":5,
              "vineable_blocks": [
                "minecraft:oak_log",
                "minecraft:spruce_log",
                "minecraft:birch_log",
                "minecraft:jungle_log",
                "minecraft:acacia_log",
                "minecraft:dark_oak_log",
                "minecraft:crimson_stem",
                "minecraft:warped_stem",
                "minecraft:stripped_oak_log",
                "minecraft:stripped_spruce_log",
                "minecraft:stripped_birch_log",
                "minecraft:stripped_jungle_log",
                "minecraft:stripped_acacia_log",
                "minecraft:stripped_dark_oak_log",
                "minecraft:stripped_crimson_stem",
                "minecraft:stripped_warped_stem",
                "minecraft:iron_ore",
                "minecraft:gold_ore",
                "minecraft:diamond_ore",
                "minecraft:emerald_ore",
                "minecraft:lapis_ore",
                "minecraft:redstone_ore",
                "minecraft:nether_quartz_ore",
                "minecraft:ancient_debris",
                "minecraft:coal_ore",
                "minecraft:copper_ore",
                "minecraft:tin_ore",
                "minecraft:lead_ore",
                "minecraft:aluminum_ore",
                "minecraft:amethyst_block",
                "minecraft:deepslate_iron_ore",
                "minecraft:deepslate_gold_ore",
                "minecraft:deepslate_diamond_ore",
                "minecraft:deepslate_emerald_ore",
                "minecraft:deepslate_lapis_ore",
                "minecraft:deepslate_redstone_ore",
                "minecraft:deepslate_copper_ore",
                "minecraft:deepslate_tin_ore",
                "minecraft:deepslate_lead_ore",
                "minecraft:deepslate_aluminum_ore",
                "minecraft:deepslate_coal_ore",
                "minecraft:deepslate_lapis_ore",
                "minecraft:deepslate_copper_ore",
                "minecraft:deepslate_tin_ore",
                "minecraft:deepslate_lead_ore",
                "minecraft:deepslate_aluminum_ore",
                "minecraft:deepslate_coal_ore"
              ]
            }""";

    // Create a keybinding for vining
    /*
    private static final String VINE_KEY_CATEGORY = "key.categories.vining";
    private static final String VINE_KEY_DESC = "key.vining.desc";
    private static final int VINE_KEY_DEFAULT_KEY = GLFW.GLFW_KEY_LEFT_SHIFT; // Default key: Left Shift
    private static KeyMapping vineKeyBinding;

     */

    // Default vineable blocks limit, sets how many blocks break per block break
    private static int VINEABLE_LIMIT = 10;

    public Viner() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for mod loading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Register the keybinding
        // vineKeyBinding = new KeyMapping(VINE_KEY_DESC, KeyConflictContext.IN_GAME, KeyModifier.NONE, VINE_KEY_DEFAULT_KEY, GLFW.GLFW_KEY_UNKNOWN, VINE_KEY_CATEGORY);
        // ClientRegistry.registerKeyBinding(vineKeyBinding);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Populate the blockMap with blockName to Block mappings
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            ResourceLocation location = ForgeRegistries.BLOCKS.getKey(block);
            if (location != null) {
                String blockName = location.toString();
                blockMap.put(blockName, block);
            }
        }

        // Get the path to your config file
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("viner/vineable_blocks.json");
        String jsonConfig;

        // Check if the config file exists, if not, create it with default settings
        if (!Files.exists(configPath)) {
            try {
                Files.createDirectories(configPath.getParent());
                Files.write(configPath, DEFAULT_CONFIG_VINEABLE.getBytes());
                LOGGER.info("Created default config file at: {}", configPath);
            } catch (IOException e) {
                LOGGER.error("Error creating default config file: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }

        // read the file in
        try {
            jsonConfig = new String(Files.readAllBytes(configPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(jsonConfig, JsonObject.class);

        // Gets the vineable blocks config
        JsonArray vineableBlocksConfigArray = jsonObject.getAsJsonArray("vineable_blocks");

        // Gets the vineable break limit
        VINEABLE_LIMIT = jsonObject.getAsJsonPrimitive("vineable_limit").getAsInt();

        // get the array of vineable_blocks from the config
        for (JsonElement blockName : vineableBlocksConfigArray.asList()) {
            VINEABLE_BLOCKS.add(blockMap.get(blockName.getAsString()));
        }
    }


    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

        }

        // Utility method to collect connected blocks of the same type
        private static void collectConnectedBlocks(Level world, BlockPos pos, BlockState targetState, List<BlockPos> connectedBlocks, Set<BlockPos> visited) {
            if (!visited.contains(pos) && targetState.getBlock().equals(world.getBlockState(pos).getBlock())) {
                visited.add(pos);
                connectedBlocks.add(pos);

                for (Direction direction : Direction.values()) {
                    collectConnectedBlocks(world, pos.offset(direction.getNormal()), targetState, connectedBlocks, visited);
                }
            }
        }

        @SubscribeEvent
        public static void onBlockBroken(Event baseEvent) {

            // ensure event is a blockBreak event
            if (!(baseEvent instanceof BlockEvent.BreakEvent event)) return;

            if (!event.getLevel().isClientSide()) {

                Player player = event.getPlayer();

                if (player != null && player.isCrouching()) {
                    BlockPos pos = event.getPos();
                    BlockState targetBlockState = event.getLevel().getBlockState(pos);

                    if (VINEABLE_BLOCKS.contains(targetBlockState.getBlock())) {
                        List<BlockPos> connectedBlocks = new ArrayList<>();
                        Set<BlockPos> visited = new HashSet<>();
                        collectConnectedBlocks((Level) event.getLevel(), pos, targetBlockState, connectedBlocks, visited);

                        int blockCount = -1;

                        // Loop through the connected blocks and break them
                        for (BlockPos connectedPos : connectedBlocks) {
                            // Only mine a configurable amount of blocks
                            if(blockCount < VINEABLE_LIMIT){
                                BlockState connectedBlockState = event.getLevel().getBlockState(connectedPos);
                                if (VINEABLE_BLOCKS.contains(connectedBlockState.getBlock())) {
                                    Block.dropResources(connectedBlockState, (Level) event.getLevel(), event.getPos());
                                    event.getLevel().removeBlock(connectedPos, false);
                                    blockCount++;
                                }
                            }
                        }

                        // update item damage
                        ItemStack item = player.getItemInHand(InteractionHand.MAIN_HAND);
                        item.setDamageValue(item.getDamageValue() + blockCount);
                    }
                }
            }
        }

    }
}
