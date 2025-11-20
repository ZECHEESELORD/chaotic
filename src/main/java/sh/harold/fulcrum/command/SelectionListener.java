package sh.harold.fulcrum.command;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import net.kyori.adventure.text.Component;
import sh.harold.fulcrum.sim.PendulumManager;

public final class SelectionListener implements Listener {

    private final Map<UUID, SelectionSession> sessions;
    private final PendulumManager manager;
    private final int maxChains;

    public SelectionListener(Map<UUID, SelectionSession> sessions, PendulumManager manager, int maxChains) {
        this.sessions = sessions;
        this.manager = manager;
        this.maxChains = maxChains;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Player player = event.getPlayer();
        final Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        final UUID playerId = player.getUniqueId();
        final SelectionSession session = this.sessions.get(playerId);
        if (session == null) {
            return;
        }
        if (session.expired()) {
            this.sessions.remove(playerId);
            return;
        }

        event.setCancelled(true);
        final int required = session.mode() == SelectionSession.SelectionMode.BUTTERFLY_CREATE ? 2 : 1;
        if (this.manager.chains().size() + required > this.maxChains) {
            player.sendMessage(Component.text("Pendulum limit reached; clear one before creating another."));
            this.sessions.remove(playerId);
            return;
        }

        if (session.mode() == SelectionSession.SelectionMode.BUTTERFLY_CREATE) {
            this.createButterfly(block.getLocation(), player);
        } else {
            final Location anchor = block.getLocation().add(0.5, 0.5, 0.5);
            final int id = this.manager.createPendulum(anchor);
            final String pos = "%s %.1f, %.1f, %.1f".formatted(
                anchor.getWorld().getName(),
                anchor.getX(),
                anchor.getY(),
                anchor.getZ()
            );
            player.sendMessage(Component.text("Created pendulum #" + id + " at " + pos + ". Configure it with /pendulum " + id + "."));
        }
        this.sessions.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(BlockDamageEvent event) {
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();
        final SelectionSession session = this.sessions.get(playerId);
        if (session == null) {
            return;
        }
        if (session.expired()) {
            this.sessions.remove(playerId);
            return;
        }
        event.setCancelled(true);
    }

    public void requestSelection(Player player) {
        this.sessions.put(player.getUniqueId(), new SelectionSession(player.getUniqueId(), Instant.now(), SelectionSession.SelectionMode.NORMAL));
        player.sendMessage(Component.text("Punch a block to pin the anchor for a new pendulum."));
    }

    public void requestButterflySelection(Player player) {
        this.sessions.put(player.getUniqueId(), new SelectionSession(player.getUniqueId(), Instant.now(), SelectionSession.SelectionMode.BUTTERFLY_CREATE));
        player.sendMessage(Component.text("Punch a block to spawn the butterfly pair demo."));
    }

    private void createButterfly(Location base, Player player) {
        final Location anchor = base.add(0.5, 0.5, 0.5);
        final int idA = this.manager.createPendulum(anchor);
        final int idB = this.manager.createPendulum(anchor);
        this.manager.configureButterfly(idA, idB);
        player.sendMessage(Component.text("Spawned butterfly pair #" + idA + " and #" + idB + " at the same anchor. Use /pendulum start " + idA + " and /pendulum start " + idB + " to watch divergence."));
    }
}
