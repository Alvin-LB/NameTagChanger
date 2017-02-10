import com.bringholm.nametagchanger.NameTagChanger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        NameTagChanger.INSTANCE.init();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args[0].equals("clear")) {
            Player player = Bukkit.getPlayer(args[1]);
            NameTagChanger.INSTANCE.resetPlayerName(player);
            return true;
        }
        Player player = Bukkit.getPlayer(args[0]);
        NameTagChanger.INSTANCE.changePlayerName(player, args[1]);
        return true;
    }
}
