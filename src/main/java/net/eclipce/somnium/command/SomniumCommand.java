package net.eclipce.somnium.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.eclipce.somnium.Somnium;
import net.eclipce.somnium.core.ability.AbilityType;
import net.eclipce.somnium.core.data.SomniumCapability;
import net.eclipce.somnium.core.data.SomniumPlayerData;
import net.eclipce.somnium.core.meter.MeterDefinition;
import net.eclipce.somnium.core.meter.MeterInstance;
import net.eclipce.somnium.core.power.Power;
import net.eclipce.somnium.core.registry.SomniumRegistries;
import net.eclipce.somnium.core.tag.TagHandler;
import net.eclipce.somnium.core.unlock.ProgressionHandler;
import net.eclipce.somnium.core.unlock.UnlockCondition;
import net.eclipce.somnium.network.SomniumNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * Brigadier command tree for the Somnium API.
 *
 * <h3>Command structure</h3>
 * <pre>
 * /somnium power add &lt;player&gt; &lt;power&gt;
 * /somnium power remove &lt;player&gt; &lt;power&gt;
 * /somnium ability add &lt;player&gt; &lt;ability&gt;
 * /somnium ability remove &lt;player&gt; &lt;ability&gt;
 * /somnium player list &lt;player&gt;
 * /somnium player state &lt;player&gt; reset &lt;power&gt;
 * /somnium player state &lt;player&gt; complete &lt;power&gt;
 * </pre>
 *
 * <p>All commands require operator permission level 2.</p>
 */
public final class SomniumCommand {

    /** Minimum permission level required to use /somnium commands. */
    private static final int PERMISSION_LEVEL = 2;

    // ═══════════════════════════════════════════════════════════════════
    //  Suggestion providers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Suggests all registered power names for tab completion.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_POWERS =
            (context, builder) -> {
                IForgeRegistry<Power> registry = SomniumRegistries.getPowerRegistry();
                if (registry != null) {
                    return SharedSuggestionProvider.suggestResource(
                            registry.getKeys(), builder);
                }
                return builder.buildFuture();
            };

    /**
     * Suggests all registered ability type names for tab completion.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ABILITIES =
            (context, builder) -> {
                IForgeRegistry<AbilityType> registry = SomniumRegistries.getAbilityTypeRegistry();
                if (registry != null) {
                    return SharedSuggestionProvider.suggestResource(
                            registry.getKeys(), builder);
                }
                return builder.buildFuture();
            };

    /**
     * Suggests powers that the target player currently has granted.
     * Used for remove/state commands where the power must already be granted.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYER_POWERS =
            (context, builder) -> {
                try {
                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                    SomniumPlayerData data = SomniumCapability.get(player);
                    if (data != null) {
                        return SharedSuggestionProvider.suggestResource(
                                data.getGrantedPowerKeys(), builder);
                    }
                } catch (Exception ignored) {
                    // Player may not be resolved yet during typing
                }
                return SUGGEST_POWERS.getSuggestions(context, builder);
            };

    // ═══════════════════════════════════════════════════════════════════
    //  Registration
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Registers the /somnium command tree with the dispatcher.
     * Called from {@link net.minecraftforge.event.RegisterCommandsEvent}.
     *
     * @param dispatcher the command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("somnium")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(buildPowerCommands())
                        .then(buildAbilityCommands())
                        .then(buildPlayerCommands())
                        .then(buildTagCommands())
                        .then(buildMeterCommands())
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    //  /somnium power
    // ═══════════════════════════════════════════════════════════════════

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
    buildPowerCommands() {
        return Commands.literal("power")
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("power", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_POWERS)
                                        .executes(SomniumCommand::powerAdd))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("power", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_PLAYER_POWERS)
                                        .executes(SomniumCommand::powerRemove))));
    }

    private static int powerAdd(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation powerKey = ResourceLocationArgument.getId(context, "power");

        Power power = SomniumRegistries.getPowerValue(powerKey);
        if (power == null) {
            context.getSource().sendFailure(
                    Component.literal("Unknown power: " + powerKey));
            return 0;
        }

        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) {
            context.getSource().sendFailure(
                    Component.literal("Could not access player data."));
            return 0;
        }

        if (data.hasPower(power)) {
            context.getSource().sendFailure(Component.literal("")
                    .append(player.getDisplayName())
                    .append(" already has power ")
                    .append(Component.literal(powerKey.toString())
                            .withStyle(ChatFormatting.GOLD)));
            return 0;
        }

        // Check required tag (commands bypass with a warning)
        if (!TagHandler.meetsRequiredTag(data, power)) {
            ResourceLocation reqTag = power.getRequiredTag();
            context.getSource().sendSuccess(() -> Component.literal("Warning: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Power requires tag " + reqTag
                            + " — granting anyway via command.")), false);
        }

        data.grantPower(power);
        SomniumNetwork.syncToClient(player);

        context.getSource().sendSuccess(() -> Component.literal("Granted power ")
                .append(Component.literal(powerKey.toString())
                        .withStyle(ChatFormatting.GOLD))
                .append(" to ")
                .append(player.getDisplayName()), true);
        return 1;
    }

    private static int powerRemove(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation powerKey = ResourceLocationArgument.getId(context, "power");

        Power power = SomniumRegistries.getPowerValue(powerKey);
        if (power == null) {
            context.getSource().sendFailure(
                    Component.literal("Unknown power: " + powerKey));
            return 0;
        }

        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) {
            context.getSource().sendFailure(
                    Component.literal("Could not access player data."));
            return 0;
        }

        if (!data.hasPower(power)) {
            context.getSource().sendFailure(Component.literal("")
                    .append(player.getDisplayName())
                    .append(" does not have power ")
                    .append(Component.literal(powerKey.toString())
                            .withStyle(ChatFormatting.GOLD)));
            return 0;
        }

        data.revokePower(power);
        SomniumNetwork.syncToClient(player);

        context.getSource().sendSuccess(() -> Component.literal("Revoked power ")
                .append(Component.literal(powerKey.toString())
                        .withStyle(ChatFormatting.GOLD))
                .append(" from ")
                .append(player.getDisplayName()), true);
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  /somnium ability
    // ═══════════════════════════════════════════════════════════════════

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
    buildAbilityCommands() {
        return Commands.literal("ability")
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("ability", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_ABILITIES)
                                        .executes(SomniumCommand::abilityAdd))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("ability", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_ABILITIES)
                                        .executes(SomniumCommand::abilityRemove))));
    }

    private static int abilityAdd(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation abilityKey = ResourceLocationArgument.getId(context, "ability");

        AbilityType abilityType = SomniumRegistries.getAbilityValue(abilityKey);
        if (abilityType == null) {
            context.getSource().sendFailure(
                    Component.literal("Unknown ability: " + abilityKey));
            return 0;
        }

        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) {
            context.getSource().sendFailure(
                    Component.literal("Could not access player data."));
            return 0;
        }

        if (data.isAbilityUnlocked(abilityType)) {
            context.getSource().sendFailure(Component.literal("")
                    .append(player.getDisplayName())
                    .append(" already has ability ")
                    .append(Component.literal(abilityKey.toString())
                            .withStyle(ChatFormatting.AQUA)));
            return 0;
        }

        // Force-unlock regardless of conditions
        data.unlockAbility(abilityType);

        // Also remove any progress tracking for this ability (it's now unlocked)
        for (ResourceLocation powerKey : data.getGrantedPowerKeys()) {
            String progressKey = powerKey.toString() + "|" + abilityKey.toString();
            data.removeUnlockProgress(progressKey);
        }

        SomniumNetwork.syncToClient(player);

        context.getSource().sendSuccess(() -> Component.literal("Unlocked ability ")
                .append(Component.literal(abilityKey.toString())
                        .withStyle(ChatFormatting.AQUA))
                .append(" for ")
                .append(player.getDisplayName()), true);
        return 1;
    }

    private static int abilityRemove(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation abilityKey = ResourceLocationArgument.getId(context, "ability");

        AbilityType abilityType = SomniumRegistries.getAbilityValue(abilityKey);
        if (abilityType == null) {
            context.getSource().sendFailure(
                    Component.literal("Unknown ability: " + abilityKey));
            return 0;
        }

        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) {
            context.getSource().sendFailure(
                    Component.literal("Could not access player data."));
            return 0;
        }

        if (!data.isAbilityUnlocked(abilityType)) {
            context.getSource().sendFailure(Component.literal("")
                    .append(player.getDisplayName())
                    .append(" does not have ability ")
                    .append(Component.literal(abilityKey.toString())
                            .withStyle(ChatFormatting.AQUA)));
            return 0;
        }

        data.lockAbility(abilityType);
        SomniumNetwork.syncToClient(player);

        context.getSource().sendSuccess(() -> Component.literal("Locked ability ")
                .append(Component.literal(abilityKey.toString())
                        .withStyle(ChatFormatting.AQUA))
                .append(" for ")
                .append(player.getDisplayName()), true);
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  /somnium player
    // ═══════════════════════════════════════════════════════════════════

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
    buildPlayerCommands() {
        return Commands.literal("player")
                .then(Commands.literal("list")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SomniumCommand::playerList)))
                .then(Commands.literal("state")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("power", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_PLAYER_POWERS)
                                                .executes(SomniumCommand::playerStateReset)))
                                .then(Commands.literal("complete")
                                        .then(Commands.argument("power", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_PLAYER_POWERS)
                                                .executes(SomniumCommand::playerStateComplete)))));
    }

    /**
     * Displays all powers, abilities, and progression state for a player.
     */
    private static int playerList(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        SomniumPlayerData data = SomniumCapability.get(player);

        if (data == null) {
            context.getSource().sendFailure(
                    Component.literal("Could not access player data."));
            return 0;
        }

        CommandSourceStack source = context.getSource();

        // Header
        source.sendSuccess(() -> Component.literal("=== Somnium Data for ")
                .append(player.getDisplayName())
                .append(" ===")
                .withStyle(ChatFormatting.GOLD), false);

        // Powers
        var powerKeys = data.getGrantedPowerKeys();
        if (powerKeys.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  No powers granted.")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            for (ResourceLocation powerKey : powerKeys) {
                Power power = SomniumRegistries.getPowerValue(powerKey);
                String powerName = power != null
                        ? power.getDisplayName().getString()
                        : powerKey.toString();

                source.sendSuccess(() -> Component.literal("  Power: ")
                        .append(Component.literal(powerName)
                                .withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" [" + powerKey + "]")
                                .withStyle(ChatFormatting.DARK_GRAY)), false);

                if (power == null) continue;

                // Show abilities in this power
                for (Power.PowerAbilityEntry entry : power.getEntries()) {
                    AbilityType abilityType = entry.getAbilityType();
                    if (abilityType == null) continue;

                    ResourceLocation abilityKey = SomniumRegistries.getAbilityKey(abilityType);
                    String abilityName = abilityType.getDisplayName().getString();
                    boolean unlocked = data.isAbilityUnlocked(abilityType);

                    if (unlocked) {
                        // Show unlocked ability
                        Component status = Component.literal("    ✓ ")
                                .withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(abilityName)
                                        .withStyle(ChatFormatting.WHITE))
                                .append(Component.literal(
                                                " [" + abilityType.getActivationType().name() + "]")
                                        .withStyle(ChatFormatting.DARK_GRAY));

                        // Show bar slot info
                        int barSlot = data.findBarSlot(abilityType);
                        if (barSlot >= 0) {
                            status = status.copy()
                                    .append(Component.literal(" (Bar " + (barSlot + 1) + ")")
                                            .withStyle(ChatFormatting.YELLOW));
                        }

                        final Component finalStatus = status;
                        source.sendSuccess(() -> finalStatus, false);
                    } else {
                        // Show locked ability with progress
                        Component status = Component.literal("    ✗ ")
                                .withStyle(ChatFormatting.RED)
                                .append(Component.literal(abilityName)
                                        .withStyle(ChatFormatting.GRAY));

                        // Show progress if tracked
                        UnlockCondition condition = entry.getUnlockCondition();
                        if (condition != null && abilityKey != null) {
                            String progressKey = ProgressionHandler.makeProgressKey(
                                    powerKey, abilityType);
                            if (progressKey != null) {
                                CompoundTag progress = data.getUnlockProgress(progressKey);
                                if (progress != null) {
                                    Component progressText = condition.getProgressText(progress);
                                    status = status.copy()
                                            .append(Component.literal(" — ")
                                                    .withStyle(ChatFormatting.DARK_GRAY))
                                            .append(progressText.copy()
                                                    .withStyle(ChatFormatting.GRAY));
                                }
                            }
                        }

                        final Component finalStatus = status;
                        source.sendSuccess(() -> finalStatus, false);
                    }
                }
            }
        }

        // Active transformation
        ResourceLocation activeTrans = data.getActiveTransformation();
        if (activeTrans != null) {
            source.sendSuccess(() -> Component.literal("  Active Transformation: ")
                    .append(Component.literal(activeTrans.toString())
                            .withStyle(ChatFormatting.LIGHT_PURPLE)), false);
        }

        // Summary counts
        int totalUnlocked = data.getUnlockedAbilityKeys().size();
        int totalProgress = data.getAllUnlockProgress().size();
        source.sendSuccess(() -> Component.literal("  Unlocked: " + totalUnlocked
                        + " abilities, " + totalProgress + " in progress")
                .withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    /**
     * Resets all progression progress for a power's locked abilities.
     * Abilities already unlocked are not affected.
     */
    private static int playerStateReset(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation powerKey = ResourceLocationArgument.getId(context, "power");

        Power power = SomniumRegistries.getPowerValue(powerKey);
        if (power == null) {
            context.getSource().sendFailure(
                    Component.literal("Unknown power: " + powerKey));
            return 0;
        }

        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) {
            context.getSource().sendFailure(
                    Component.literal("Could not access player data."));
            return 0;
        }

        if (!data.hasPower(power)) {
            context.getSource().sendFailure(Component.literal("")
                    .append(player.getDisplayName())
                    .append(" does not have power ")
                    .append(Component.literal(powerKey.toString())
                            .withStyle(ChatFormatting.GOLD)));
            return 0;
        }

        // Remove existing progress and re-initialize
        ProgressionHandler.removeProgressForPower(data, power);
        if (power.isProgressionEnabled()) {
            ProgressionHandler.initializeProgressForPower(data, power);
        }

        SomniumNetwork.syncToClient(player);

        context.getSource().sendSuccess(() -> Component.literal("Reset progression for ")
                .append(Component.literal(powerKey.toString())
                        .withStyle(ChatFormatting.GOLD))
                .append(" on ")
                .append(player.getDisplayName()), true);
        return 1;
    }

    /**
     * Force-unlocks all abilities in a power, bypassing progression conditions.
     */
    private static int playerStateComplete(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation powerKey = ResourceLocationArgument.getId(context, "power");

        Power power = SomniumRegistries.getPowerValue(powerKey);
        if (power == null) {
            context.getSource().sendFailure(
                    Component.literal("Unknown power: " + powerKey));
            return 0;
        }

        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) {
            context.getSource().sendFailure(
                    Component.literal("Could not access player data."));
            return 0;
        }

        if (!data.hasPower(power)) {
            context.getSource().sendFailure(Component.literal("")
                    .append(player.getDisplayName())
                    .append(" does not have power ")
                    .append(Component.literal(powerKey.toString())
                            .withStyle(ChatFormatting.GOLD)));
            return 0;
        }

        int unlocked = 0;
        for (AbilityType abilityType : power.getAbilityTypes()) {
            if (!data.isAbilityUnlocked(abilityType)) {
                data.unlockAbility(abilityType, power);
                unlocked++;
            }
        }

        // Clean up all progress entries for this power
        ProgressionHandler.removeProgressForPower(data, power);

        SomniumNetwork.syncToClient(player);

        final int count = unlocked;
        context.getSource().sendSuccess(() -> Component.literal("Force-unlocked "
                        + count + " abilities in ")
                .append(Component.literal(powerKey.toString())
                        .withStyle(ChatFormatting.GOLD))
                .append(" for ")
                .append(player.getDisplayName()), true);
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  /somnium tag
    // ═══════════════════════════════════════════════════════════════════

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
    buildTagCommands() {
        return Commands.literal("tag")
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("tag", ResourceLocationArgument.id())
                                        .executes(SomniumCommand::tagAdd))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("tag", ResourceLocationArgument.id())
                                        .executes(SomniumCommand::tagRemove))))
                .then(Commands.literal("list")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SomniumCommand::tagList)));
    }

    private static int tagAdd(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation tag = ResourceLocationArgument.getId(context, "tag");

        boolean added = TagHandler.addTag(player, tag);

        if (added) {
            context.getSource().sendSuccess(() -> Component.literal("Added tag ")
                    .append(Component.literal(tag.toString())
                            .withStyle(ChatFormatting.GREEN))
                    .append(" to ")
                    .append(player.getDisplayName()), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("")
                    .append(player.getDisplayName())
                    .append(" already has tag ")
                    .append(Component.literal(tag.toString())
                            .withStyle(ChatFormatting.GREEN)));
            return 0;
        }
    }

    private static int tagRemove(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation tag = ResourceLocationArgument.getId(context, "tag");

        boolean removed = TagHandler.removeTag(player, tag);

        if (removed) {
            context.getSource().sendSuccess(() -> Component.literal("Removed tag ")
                    .append(Component.literal(tag.toString())
                            .withStyle(ChatFormatting.GREEN))
                    .append(" from ")
                    .append(player.getDisplayName()), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("")
                    .append(player.getDisplayName())
                    .append(" does not have tag ")
                    .append(Component.literal(tag.toString())
                            .withStyle(ChatFormatting.GREEN)));
            return 0;
        }
    }

    private static int tagList(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        SomniumPlayerData data = SomniumCapability.get(player);

        if (data == null) {
            context.getSource().sendFailure(
                    Component.literal("Could not access player data."));
            return 0;
        }

        var tags = data.getTags();
        context.getSource().sendSuccess(() -> Component.literal("=== Tags for ")
                .append(player.getDisplayName())
                .append(" ===")
                .withStyle(ChatFormatting.GREEN), false);

        if (tags.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("  No tags.")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            for (ResourceLocation tag : tags) {
                context.getSource().sendSuccess(() -> Component.literal("  - ")
                        .append(Component.literal(tag.toString())
                                .withStyle(ChatFormatting.GREEN)), false);
            }
        }

        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  /somnium meter
    // ═══════════════════════════════════════════════════════════════════

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
    buildMeterCommands() {
        return Commands.literal("meter")
                // /somnium meter stamina ...
                .then(Commands.literal("stamina")
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(SomniumCommand::meterStaminaGet)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(0))
                                                .executes(SomniumCommand::meterStaminaSet))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                .executes(SomniumCommand::meterStaminaAdd))))
                        .then(Commands.literal("max")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(1))
                                                .executes(SomniumCommand::meterStaminaMax)))))
                // /somnium meter custom ...
                .then(Commands.literal("custom")
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("meter", ResourceLocationArgument.id())
                                                .executes(SomniumCommand::meterCustomGet))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("meter", ResourceLocationArgument.id())
                                                .then(Commands.argument("value", FloatArgumentType.floatArg(0))
                                                        .executes(SomniumCommand::meterCustomSet)))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("meter", ResourceLocationArgument.id())
                                                .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                        .executes(SomniumCommand::meterCustomAdd)))))
                        .then(Commands.literal("max")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("meter", ResourceLocationArgument.id())
                                                .then(Commands.argument("value", FloatArgumentType.floatArg(1))
                                                        .executes(SomniumCommand::meterCustomMax))))));
    }

    // --- Stamina commands ---

    private static int meterStaminaGet(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) { context.getSource().sendFailure(Component.literal("No data.")); return 0; }

        net.eclipce.somnium.core.meter.StaminaData stamina = data.getStaminaData();
        context.getSource().sendSuccess(() -> Component.literal("Stamina for ")
                .append(player.getDisplayName())
                .append(": ")
                .append(Component.literal(String.format("%.1f / %.1f", stamina.getValue(), stamina.getMaxValue()))
                        .withStyle(ChatFormatting.YELLOW))
                .append(stamina.isInOveruse()
                        ? Component.literal(" [OVERUSE Stage " + stamina.getOveruseStage() + "]")
                        .withStyle(ChatFormatting.RED)
                        : Component.literal("")), false);
        return 1;
    }

    private static int meterStaminaSet(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        float value = FloatArgumentType.getFloat(context, "value");
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) { context.getSource().sendFailure(Component.literal("No data.")); return 0; }

        data.getStaminaData().setValue(value);
        SomniumNetwork.syncToClient(player);
        context.getSource().sendSuccess(() -> Component.literal("Set stamina to ")
                .append(Component.literal(String.format("%.1f", value))
                        .withStyle(ChatFormatting.YELLOW))
                .append(" for ").append(player.getDisplayName()), true);
        return 1;
    }

    private static int meterStaminaAdd(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        float value = FloatArgumentType.getFloat(context, "value");
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) { context.getSource().sendFailure(Component.literal("No data.")); return 0; }

        net.eclipce.somnium.core.meter.StaminaData stamina = data.getStaminaData();
        if (value >= 0) stamina.add(value); else stamina.drain(-value);
        SomniumNetwork.syncToClient(player);
        context.getSource().sendSuccess(() -> Component.literal("Stamina now ")
                .append(Component.literal(String.format("%.1f / %.1f", stamina.getValue(), stamina.getMaxValue()))
                        .withStyle(ChatFormatting.YELLOW))
                .append(" for ").append(player.getDisplayName()), true);
        return 1;
    }

    private static int meterStaminaMax(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        float value = FloatArgumentType.getFloat(context, "value");
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) { context.getSource().sendFailure(Component.literal("No data.")); return 0; }

        data.getStaminaData().setMaxValue(value);
        SomniumNetwork.syncToClient(player);
        context.getSource().sendSuccess(() -> Component.literal("Set stamina max to ")
                .append(Component.literal(String.format("%.1f", value))
                        .withStyle(ChatFormatting.YELLOW))
                .append(" for ").append(player.getDisplayName()), true);
        return 1;
    }

    // --- Custom meter commands ---

    private static int meterCustomGet(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation meterId = ResourceLocationArgument.getId(context, "meter");
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) { context.getSource().sendFailure(Component.literal("No data.")); return 0; }

        MeterInstance meter = data.getMeter(meterId);
        if (meter == null) {
            context.getSource().sendFailure(Component.literal("Unknown meter: " + meterId));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Meter ")
                .append(Component.literal(meterId.toString()).withStyle(ChatFormatting.GREEN))
                .append(" for ").append(player.getDisplayName())
                .append(": ")
                .append(Component.literal(String.format("%.1f / %.1f", meter.getValue(), meter.getMaxValue()))
                        .withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    private static int meterCustomSet(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation meterId = ResourceLocationArgument.getId(context, "meter");
        float value = FloatArgumentType.getFloat(context, "value");
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) { context.getSource().sendFailure(Component.literal("No data.")); return 0; }

        MeterInstance meter = data.getMeter(meterId);
        if (meter == null) {
            context.getSource().sendFailure(Component.literal("Unknown meter: " + meterId));
            return 0;
        }
        meter.setValue(value);
        SomniumNetwork.syncToClient(player);
        context.getSource().sendSuccess(() -> Component.literal("Set ")
                .append(Component.literal(meterId.toString()).withStyle(ChatFormatting.GREEN))
                .append(" to ").append(Component.literal(String.format("%.1f", value))
                        .withStyle(ChatFormatting.YELLOW))
                .append(" for ").append(player.getDisplayName()), true);
        return 1;
    }

    private static int meterCustomAdd(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation meterId = ResourceLocationArgument.getId(context, "meter");
        float value = FloatArgumentType.getFloat(context, "value");
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) { context.getSource().sendFailure(Component.literal("No data.")); return 0; }

        MeterInstance meter = data.getMeter(meterId);
        if (meter == null) {
            context.getSource().sendFailure(Component.literal("Unknown meter: " + meterId));
            return 0;
        }
        if (value >= 0) meter.add(value); else meter.drain(-value);
        SomniumNetwork.syncToClient(player);
        context.getSource().sendSuccess(() -> Component.literal("Meter ")
                .append(Component.literal(meterId.toString()).withStyle(ChatFormatting.GREEN))
                .append(" now ").append(Component.literal(String.format("%.1f / %.1f", meter.getValue(), meter.getMaxValue()))
                        .withStyle(ChatFormatting.YELLOW))
                .append(" for ").append(player.getDisplayName()), true);
        return 1;
    }

    private static int meterCustomMax(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation meterId = ResourceLocationArgument.getId(context, "meter");
        float value = FloatArgumentType.getFloat(context, "value");
        SomniumPlayerData data = SomniumCapability.get(player);
        if (data == null) { context.getSource().sendFailure(Component.literal("No data.")); return 0; }

        MeterInstance meter = data.getMeter(meterId);
        if (meter == null) {
            context.getSource().sendFailure(Component.literal("Unknown meter: " + meterId));
            return 0;
        }
        meter.setMaxValue(value);
        SomniumNetwork.syncToClient(player);
        context.getSource().sendSuccess(() -> Component.literal("Set ")
                .append(Component.literal(meterId.toString()).withStyle(ChatFormatting.GREEN))
                .append(" max to ").append(Component.literal(String.format("%.1f", value))
                        .withStyle(ChatFormatting.YELLOW))
                .append(" for ").append(player.getDisplayName()), true);
        return 1;
    }

    // Private constructor — utility class
    private SomniumCommand() {}
}