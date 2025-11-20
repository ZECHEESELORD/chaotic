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
        final PendulumChain chain = new PendulumChain(id, anchor);
        this.chainsById.put(id, chain);
        this.scheduleChain(chain);
        return id;
    }

    public void remove(int id) {
        final ScheduledTask task = this.scheduledTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
        this.chainsById.remove(id);
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
}
