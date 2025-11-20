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
        if (this.manager.chains().size() >= this.maxChains) {
            player.sendMessage(Component.text("Pendulum limit reached; clear one before creating another."));
            this.sessions.remove(playerId);
            return;
        }

        final Location anchor = block.getLocation().add(0.5, 1.0, 0.5);
        final int id = this.manager.createPendulum(anchor);
        this.sessions.remove(playerId);

        final String pos = "%s %.1f, %.1f, %.1f".formatted(
            anchor.getWorld().getName(),
            anchor.getX(),
            anchor.getY(),
            anchor.getZ()
        );
        player.sendMessage(Component.text("Created pendulum #" + id + " at " + pos + ". Configure it with /pendulum " + id + "."));
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
        this.sessions.put(player.getUniqueId(), new SelectionSession(player.getUniqueId(), Instant.now()));
        player.sendMessage(Component.text("Punch a block to pin the anchor for a new pendulum."));
    }
}
