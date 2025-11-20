package sh.harold.fulcrum.sim;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import sh.harold.fulcrum.physics.PendulumChain;
import sh.harold.fulcrum.physics.ParticleStyle;
import sh.harold.fulcrum.physics.TipTrailStyle;

public final class PendulumManager {

    private final Plugin plugin;
    private final Map<Integer, PendulumChain> chainsById = new ConcurrentHashMap<>();
    private final Map<Integer, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public PendulumManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public Collection<PendulumChain> chains() {
        return this.chainsById.values();
    }

    public Optional<PendulumChain> get(int id) {
        return Optional.ofNullable(this.chainsById.get(id));
    }

    public int createPendulum(Location anchor) {
        final int id = this.nextId.getAndIncrement();
        final PendulumChain chain = new PendulumChain(id, anchor, this.plugin);
        this.chainsById.put(id, chain);
        this.scheduleChain(chain);
        return id;
    }

    public void remove(int id) {
        final ScheduledTask task = this.scheduledTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
        final PendulumChain chain = this.chainsById.remove(id);
        if (chain != null) {
            chain.cleanupEntities();
        }
    }

    public void tickAll(double dtTick, World world) {
        for (final PendulumChain chain : this.chainsById.values()) {
            final Location anchor = chain.anchor();
            if (anchor.getWorld() != world) {
                continue;
            }
            if (!world.isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4)) {
                continue;
            }
            chain.stepTick(dtTick);
            chain.render(world);
        }
    }

    private void scheduleChain(PendulumChain chain) {
        final Location anchor = chain.anchor();
        final ScheduledTask old = this.scheduledTasks.remove(chain.id());
        if (old != null) {
            old.cancel();
        }

        final ScheduledTask task = Bukkit.getRegionScheduler().runAtFixedRate(
            this.plugin,
            anchor,
            scheduledTask -> {
                final World world = anchor.getWorld();
                if (world == null) {
                    return;
                }
                if (!world.isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4)) {
                    return;
                }
                chain.stepTick(0.05);
                chain.render(world);
            },
            1L,
            1L
        );
        this.scheduledTasks.put(chain.id(), task);
    }

    public void configureButterfly(int idA, int idB) {
        final PendulumChain a = this.chainsById.get(idA);
        final PendulumChain b = this.chainsById.get(idB);
        if (a == null || b == null) {
            return;
        }
        final double baseAngle = -Math.PI / 2 + 0.75;
        final double delta = 0.0001;

        for (PendulumChain chain : new PendulumChain[]{a, b}) {
            chain.configureSegments(2);
            chain.setSegmentLength(0, 1.4);
            chain.setSegmentLength(1, 1.4);
            chain.setMass(1, 1.0);
            chain.setMass(2, 1.0);
            chain.particleStyle(ParticleStyle.CHICKEN);
            chain.setItemParticles(new org.bukkit.inventory.ItemStack(org.bukkit.Material.CHICKEN), new org.bukkit.inventory.ItemStack(org.bukkit.Material.COOKED_CHICKEN));
            chain.tipTrailStyle(TipTrailStyle.CHERRY);
            chain.traceTip(true);
            chain.showNodes(true);
            chain.nodeParticleSize(1.2f);
            chain.drag(0.01);
            chain.substeps(12);
            chain.iterations(10);
            chain.scale(2.2);
            chain.active(false);
        }
        a.setPoseAngles(baseAngle, baseAngle);
        b.setPoseAngles(baseAngle + delta, baseAngle + delta);
        a.active(true);
        b.active(true);
    }
}
