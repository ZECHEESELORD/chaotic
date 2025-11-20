package sh.harold.fulcrum;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import sh.harold.fulcrum.command.SelectionListener;
import sh.harold.fulcrum.command.SelectionSession;
import sh.harold.fulcrum.dialog.PendulumDialogService;
import sh.harold.fulcrum.physics.PendulumChain;
import sh.harold.fulcrum.physics.PoseType;
import sh.harold.fulcrum.sim.PendulumManager;

public final class Chaotic extends JavaPlugin {

    private static final int MAX_CHAINS = 24;

    private final Map<UUID, SelectionSession> selectionSessions = new ConcurrentHashMap<>();
    private PendulumManager manager;
    private SelectionListener selectionListener;
    private PendulumDialogService dialogService;

    @Override
    public void onEnable() {
        if (!dialogAvailable()) {
            this.getLogger().severe("Paper dialog API missing (requires Paper 1.21.6+). Update your server jar to a recent 1.21.x build.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.manager = new PendulumManager(this);
        this.selectionListener = new SelectionListener(this.selectionSessions, this.manager, MAX_CHAINS);
        this.dialogService = new PendulumDialogService(this, this.manager);

        this.getServer().getPluginManager().registerEvents(this.selectionListener, this);
        this.registerCommands();
    }
    private boolean dialogAvailable() {
        try {
            Class.forName("net.kyori.adventure.dialog.DialogLike", false, this.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private void registerCommands() {
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(this.buildRootCommand());
        });
    }

    private LiteralCommandNode<CommandSourceStack> buildRootCommand() {
        return Commands.literal("pendulum")
            .requires(stack -> stack.getSender() instanceof Player)
            .then(Commands.literal("setpoint").executes(ctx -> {
                final Player player = playerOrWarn(ctx.getSource());
                if (player == null) {
                    return Command.SINGLE_SUCCESS;
                }
                if (this.manager.chains().size() >= MAX_CHAINS) {
                    player.sendMessage(Component.text("At the pendulum cap. Remove one before adding another."));
                    return Command.SINGLE_SUCCESS;
                }
                this.selectionListener.requestSelection(player);
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("start").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
                final Player player = playerOrWarn(ctx.getSource());
                if (player == null) {
                    return Command.SINGLE_SUCCESS;
                }
                final int id = IntegerArgumentType.getInteger(ctx, "id");
                final PendulumChain chain = this.manager.get(id).orElse(null);
                if (chain == null) {
                    player.sendMessage(Component.text("No pendulum #" + id + " exists."));
                    return Command.SINGLE_SUCCESS;
                }
                if (!chain.configured()) {
                    player.sendMessage(Component.text("Pendulum #" + id + " is not configured yet. Run /pendulum " + id + " first."));
                    return Command.SINGLE_SUCCESS;
                }
                chain.active(true);
                player.sendMessage(Component.text("Pendulum #" + id + " set to swing."));
                return Command.SINGLE_SUCCESS;
            })))
            .then(Commands.literal("stop").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
                final Player player = playerOrWarn(ctx.getSource());
                if (player == null) {
                    return Command.SINGLE_SUCCESS;
                }
                final int id = IntegerArgumentType.getInteger(ctx, "id");
                final PendulumChain chain = this.manager.get(id).orElse(null);
                if (chain == null) {
                    player.sendMessage(Component.text("No pendulum #" + id + " exists."));
                    return Command.SINGLE_SUCCESS;
                }
                chain.active(false);
                player.sendMessage(Component.text("Pendulum #" + id + " frozen mid-air."));
                return Command.SINGLE_SUCCESS;
            })))
            .then(Commands.literal("setpos").then(Commands.argument("pose", StringArgumentType.word())
                .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
                    final Player player = playerOrWarn(ctx.getSource());
                    if (player == null) {
                        return Command.SINGLE_SUCCESS;
                    }
                    final PoseType pose = parsePose(StringArgumentType.getString(ctx, "pose"));
                    if (pose == null) {
                        player.sendMessage(Component.text("Pose must be one of up, down, left, right, randomize."));
                        return Command.SINGLE_SUCCESS;
                    }
                    final int id = IntegerArgumentType.getInteger(ctx, "id");
                    final PendulumChain chain = this.manager.get(id).orElse(null);
                    if (chain == null) {
                        player.sendMessage(Component.text("No pendulum #" + id + " exists."));
                        return Command.SINGLE_SUCCESS;
                    }
                    chain.active(false);
                    chain.resetPose(pose, ThreadLocalRandom.current());
                    player.sendMessage(Component.text("Pendulum #" + id + " reset to " + pose.name().toLowerCase() + " pose."));
                    return Command.SINGLE_SUCCESS;
                }))))
            .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
                final Player player = playerOrWarn(ctx.getSource());
                if (player == null) {
                    return Command.SINGLE_SUCCESS;
                }
                final int id = IntegerArgumentType.getInteger(ctx, "id");
                this.dialogService.openSetup(player, id);
                return Command.SINGLE_SUCCESS;
            })).build();
    }

    private static Player playerOrWarn(CommandSourceStack source) {
        final CommandSender sender = source.getSender();
        if (sender instanceof Player player) {
            return player;
        }
        return null;
    }

    private static PoseType parsePose(String raw) {
        final String normalized = raw.toUpperCase();
        return switch (normalized) {
            case "UP" -> PoseType.UP;
            case "DOWN" -> PoseType.DOWN;
            case "LEFT" -> PoseType.LEFT;
            case "RIGHT" -> PoseType.RIGHT;
            case "RANDOMIZE", "RANDOM", "RANDOMIZED" -> PoseType.RANDOMIZED;
            default -> null;
        };
    }
}
