package sh.harold.fulcrum.dialog;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.RegistryBuilderFactory;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.physics.PendulumChain;
import sh.harold.fulcrum.physics.ParticleStyle;
import sh.harold.fulcrum.physics.PoseType;
import sh.harold.fulcrum.physics.TipTrailStyle;
import sh.harold.fulcrum.sim.PendulumManager;

public final class PendulumDialogService {

    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
        .uses(ClickCallback.UNLIMITED_USES)
        .lifetime(Duration.ofMinutes(20))
        .build();

    private static final int MAX_SEGMENTS = 12;
    private static final double LENGTH_MIN = 0.5;
    private static final double LENGTH_MAX = 3.0;
    private static final double MASS_MIN = 0.1;
    private static final double MASS_MAX = 25.0;

    private final Plugin plugin;
    private final PendulumManager manager;

    public PendulumDialogService(Plugin plugin, PendulumManager manager) {
        this.manager = manager;
        this.plugin = plugin;
    }

    public void openSetup(Player player, int pendulumId) {
        final PendulumChain chain = this.manager.get(pendulumId).orElse(null);
        if (chain == null) {
            player.sendMessage(Component.text("No pendulum with id " + pendulumId + " found."));
            return;
        }
        player.showDialog(this.segmentCountDialog(chain));
    }

    private Dialog segmentCountDialog(PendulumChain chain) {
        final int currentSegments = Math.max(1, chain.segmentCount() == 0 ? 3 : chain.segmentCount());
        final List<DialogBody> body = List.of(
            DialogBody.plainMessage(Component.text("Pendulum #" + chain.id() + " in " + chain.anchor().getWorld().getName(), NamedTextColor.GOLD)),
            DialogBody.plainMessage(Component.text("Step 1: Choose how many links to simulate.", NamedTextColor.GRAY))
        );
        final List<DialogInput> inputs = List.of(
            DialogInput.numberRange("segments", Component.text("Segments"), 1.0f, (float) MAX_SEGMENTS)
                .width(200)
                .labelFormat("%s: %s")
                .initial((float) currentSegments)
                .step(1.0f)
                .build()
        );

        final ActionButton next = ActionButton.builder(Component.text("Next"))
            .width(120)
            .action(customClick((response, audience) -> this.runSync(() -> {
                final Player player = asPlayer(audience);
                if (player == null) {
                    return;
                }
                final int requested = readInt(response.getFloat("segments"), 1, MAX_SEGMENTS, currentSegments);
                chain.active(false);
                chain.configureSegments(requested);
                chain.resetPose(PoseType.DOWN, ThreadLocalRandom.current());
                player.showDialog(this.segmentDialog(chain, 0));
            })))
            .build();

        final DialogBase base = DialogBase.builder(Component.text("Configure pendulum"))
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogAfterAction.CLOSE)
            .body(body)
            .inputs(inputs)
            .build();

        final DialogType type = DialogType.multiAction(List.of(next), null, 1);
        return buildDialog(base, type);
    }

    private Dialog segmentDialog(PendulumChain chain, int segmentIndex) {
        final int nodeIndex = segmentIndex + 1;
        final double[] lengths = chain.segmentLengths();
        final double length = lengths[segmentIndex];
        final double mass = chain.massAt(nodeIndex);
        final int total = chain.segmentCount();

        final List<DialogBody> body = List.of(
            DialogBody.plainMessage(Component.text("Segment " + (segmentIndex + 1) + " of " + total, NamedTextColor.GOLD)),
            DialogBody.plainMessage(Component.text("Step 2: Tune length and bob mass for this link.", NamedTextColor.GRAY))
        );

        final List<DialogInput> inputs = List.of(
            DialogInput.numberRange("length", Component.text("Length (Meters)"), (float) LENGTH_MIN, (float) LENGTH_MAX)
                .width(220)
                .labelFormat("%s: %s")
                .initial((float) length)
                .step(0.05f)
                .build(),
            DialogInput.numberRange("mass", Component.text("Mass (Kg)"), (float) MASS_MIN, (float) MASS_MAX)
                .width(220)
                .labelFormat("%s: %s")
                .initial((float) mass)
                .step(0.1f)
                .build()
        );

        final ActionButton back = ActionButton.builder(Component.text("Back"))
            .width(110)
            .action(customClick((response, audience) -> this.runSync(() -> {
                final Player player = asPlayer(audience);
                if (player == null) {
                    return;
                }
                applySegmentInputs(response, chain, segmentIndex);
                if (segmentIndex == 0) {
                    player.showDialog(this.segmentCountDialog(chain));
                } else {
                    player.showDialog(this.segmentDialog(chain, segmentIndex - 1));
                }
            })))
            .build();

        final boolean last = segmentIndex == total - 1;
        final ActionButton next = ActionButton.builder(Component.text(last ? "Review" : "Next"))
            .width(120)
            .action(customClick((response, audience) -> this.runSync(() -> {
                final Player player = asPlayer(audience);
                if (player == null) {
                    return;
                }
                applySegmentInputs(response, chain, segmentIndex);
                if (last) {
                    player.showDialog(this.summaryDialog(chain));
                } else {
                    player.showDialog(this.segmentDialog(chain, segmentIndex + 1));
                }
            })))
            .build();

        final DialogBase base = DialogBase.builder(Component.text("Segment builder"))
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogAfterAction.CLOSE)
            .body(body)
            .inputs(inputs)
            .build();

        final DialogType type = DialogType.multiAction(List.of(back, next), null, 2);
        return buildDialog(base, type);
    }

    private Dialog summaryDialog(PendulumChain chain) {
        final StringBuilder summary = new StringBuilder();
        final double[] lengths = chain.segmentLengths();
        for (int i = 0; i < lengths.length; i++) {
            final double mass = chain.massAt(i + 1);
            summary.append("Link ").append(i + 1).append(": ")
                .append("%.2fm".formatted(lengths[i]))
                .append(" | mass ").append("%.2fkg".formatted(mass));
            if (i < lengths.length - 1) {
                summary.append("\n");
            }
        }

        final List<DialogBody> body = List.of(
            DialogBody.plainMessage(Component.text("Step 3: Review & polish", NamedTextColor.GOLD)),
            DialogBody.plainMessage(Component.text("Style: " + chain.particleStyle().name().toLowerCase(), NamedTextColor.AQUA)),
            DialogBody.plainMessage(Component.text(summary.toString(), NamedTextColor.GRAY), 320)
        );

        final List<DialogInput> inputs = List.of(
            DialogInput.numberRange("scale", Component.text("Scale (Blocks per Meter)"), 1.0f, 5.0f)
                .width(200)
                .labelFormat("%s: %s")
                .initial((float) chain.scale())
                .step(0.1f)
                .build(),
            DialogInput.singleOption("style", Component.text("Particle Style"), styleOptions(chain.particleStyle()))
                .width(200)
                .labelVisible(true)
                .build(),
            DialogInput.bool("trace", Component.text("Trace Tip"), chain.traceTip(), "true", "false"),
            DialogInput.singleOption("tipStyle", Component.text("Tip Trail Style"), tipStyleOptions(chain.tipTrailStyle()))
                .width(200)
                .labelVisible(true)
                .build(),
            DialogInput.numberRange("drag", Component.text("Drag"), 0.001f, 0.05f)
                .width(200)
                .labelFormat("%s: %s")
                .initial((float) chain.drag())
                .step(0.001f)
                .build(),
            DialogInput.numberRange("substeps", Component.text("Substeps"), 1.0f, 40.0f)
                .width(200)
                .labelFormat("%s: %s")
                .initial((float) chain.substeps())
                .step(1.0f)
                .build(),
            DialogInput.numberRange("iterations", Component.text("Iterations"), 1.0f, 20.0f)
                .width(200)
                .labelFormat("%s: %s")
                .initial((float) chain.iterations())
                .step(1.0f)
                .build(),
            DialogInput.numberRange("gravity", Component.text("Gravity"), 5.0f, 15.0f)
                .width(200)
                .labelFormat("%s: %s")
                .initial((float) chain.gravity())
                .step(0.05f)
                .build(),
            DialogInput.bool("nodes", Component.text("Show Node Particles"), chain.showNodes(), "true", "false"),
            DialogInput.numberRange("nodeSize", Component.text("Node Particle Size"), 0.2f, 2.5f)
                .width(200)
                .labelFormat("%s: %s")
                .initial(chain.nodeParticleSize())
                .step(0.1f)
                .build()
        );

        final ActionButton back = ActionButton.builder(Component.text("Edit last link"))
            .width(150)
            .action(customClick((response, audience) -> this.runSync(() -> {
                final Player player = asPlayer(audience);
                if (player == null) {
                    return;
                }
                player.showDialog(this.segmentDialog(chain, Math.max(0, chain.segmentCount() - 1)));
            })))
            .build();

        final ActionButton apply = ActionButton.builder(Component.text("Save"))
            .width(120)
            .action(customClick((response, audience) -> this.runSync(() -> {
                final Player player = asPlayer(audience);
                if (player == null) {
                    return;
                }
                final double scale = readDouble(response.getFloat("scale"), chain.scale(), 1.0, 5.0);
                final double drag = readDouble(response.getFloat("drag"), chain.drag(), 0.001, 0.05);
                final int substeps = readInt(response.getFloat("substeps"), 1, 80, chain.substeps());
                final int iterations = readInt(response.getFloat("iterations"), 1, 30, chain.iterations());
                final double gravity = readDouble(response.getFloat("gravity"), chain.gravity(), 5.0, 15.0);
                final ParticleStyle style = parseStyle(response.getText("style"), chain.particleStyle());
                final Boolean trace = response.getBoolean("trace");
                final TipTrailStyle tipStyle = parseTipStyle(response.getText("tipStyle"), chain.tipTrailStyle());
                final Boolean nodes = response.getBoolean("nodes");
                final double nodeSize = readDouble(response.getFloat("nodeSize"), chain.nodeParticleSize(), 0.2, 2.5);
                chain.scale(scale);
                chain.particleStyle(style);
                if (nodes != null) {
                    chain.showNodes(nodes);
                }
                chain.nodeParticleSize((float) nodeSize);
                chain.tipTrailStyle(tipStyle);
                chain.drag(drag);
                chain.substeps(substeps);
                chain.iterations(iterations);
                chain.gravity(gravity);
                chain.traceTip(trace != null ? trace : chain.traceTip());
                chain.resetPose(PoseType.DOWN, ThreadLocalRandom.current());
                audience.closeDialog();
                player.sendMessage(Component.text("Pendulum #" + chain.id() + " updated. Use /pendulum start " + chain.id() + " to swing."));
            })))
            .build();

        final DialogBase base = DialogBase.builder(Component.text("Finalize configuration"))
            .canCloseWithEscape(true)
            .pause(false)
            .afterAction(DialogAfterAction.CLOSE)
            .body(body)
            .inputs(inputs)
            .build();

        final DialogType type = DialogType.multiAction(List.of(back, apply), null, 2);
        return buildDialog(base, type);
    }

    private Dialog buildDialog(DialogBase base, DialogType type) {
        return Dialog.create(factory -> this.populate(factory, base, type));
    }

    private void runSync(Runnable action) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, action);
    }

    private void populate(RegistryBuilderFactory<Dialog, ? extends DialogRegistryEntry.Builder> factory, DialogBase base, DialogType type) {
        final DialogRegistryEntry.Builder builder = factory.empty();
        builder.base(base);
        builder.type(type);
    }

    private DialogAction customClick(DialogActionCallback callback) {
        return DialogAction.customClick(callback, CALLBACK_OPTIONS);
    }

    private Player asPlayer(Audience audience) {
        return audience instanceof Player player ? player : null;
    }

    private static int readInt(Float value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        final int rounded = Math.round(value);
        return Math.max(min, Math.min(max, rounded));
    }

    private static double readDouble(Float value, double fallback, double min, double max) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value.doubleValue()));
    }

    private void applySegmentInputs(DialogResponseView response, PendulumChain chain, int segmentIndex) {
        final double length = readDouble(response.getFloat("length"), chain.segmentLengths()[segmentIndex], LENGTH_MIN, LENGTH_MAX);
        final double mass = readDouble(response.getFloat("mass"), chain.massAt(segmentIndex + 1), MASS_MIN, MASS_MAX);
        chain.setSegmentLength(segmentIndex, length);
        chain.setMass(segmentIndex + 1, mass);
        chain.active(false);
    }

    private List<SingleOptionDialogInput.OptionEntry> styleOptions(ParticleStyle current) {
        final List<SingleOptionDialogInput.OptionEntry> entries = new java.util.ArrayList<>();
        for (final ParticleStyle style : ParticleStyle.values()) {
            final String label = switch (style) {
                case WEIGHTED -> "Weighted color blend";
                case SPARK -> "Electric spark";
                case BUBBLE -> "Bubble";
                case BUBBLE_COLUMN_UP -> "Bubble column";
                case BUBBLE_POP -> "Bubble pop";
            };
            entries.add(SingleOptionDialogInput.OptionEntry.create(style.name().toLowerCase(), Component.text(label), style == current));
        }
        return entries;
    }

    private ParticleStyle parseStyle(String id, ParticleStyle fallback) {
        if (id == null) {
            return fallback;
        }
        try {
            return ParticleStyle.valueOf(id.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private List<SingleOptionDialogInput.OptionEntry> tipStyleOptions(TipTrailStyle current) {
        final List<SingleOptionDialogInput.OptionEntry> entries = new java.util.ArrayList<>();
        for (final TipTrailStyle style : TipTrailStyle.values()) {
            final String label = switch (style) {
                case END_ROD -> "End rod trail";
                case FALLING -> "Falling tear trail";
                case CHERRY -> "Cherry leaves trail";
            };
            entries.add(SingleOptionDialogInput.OptionEntry.create(style.name().toLowerCase(), Component.text(label), style == current));
        }
        return entries;
    }

    private TipTrailStyle parseTipStyle(String id, TipTrailStyle fallback) {
        if (id == null) {
            return fallback;
        }
        try {
            return TipTrailStyle.valueOf(id.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    @SafeVarargs
    private List<DialogInput> merge(List<DialogInput>... lists) {
        final List<DialogInput> merged = new java.util.ArrayList<>();
        for (final List<DialogInput> list : lists) {
            merged.addAll(list);
        }
        return merged;
    }
}
