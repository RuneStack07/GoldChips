import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main extends ListenerAdapter {

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        // Database ko initialize karein
        String mongoUri = dotenv.get("MONGO_URI");
        Database.init(mongoUri);

        String token = dotenv.get("DISCORD_BOT_TOKEN");

        JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                // WalletSystem yahan add ho gaya hai
                .addEventListeners(new Main(), new WalletSystem(), new Flowerpoker(), new Hotcold())
                .build()
                .updateCommands().addCommands(
                        Commands.slash("wallet", "Check your gold chips wallet"),
                        Commands.slash("getwallet", "Admin: Security Check & Management")
                                .addOption(OptionType.USER, "user", "Target user", true)
                                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                        Commands.slash("fp", "Staff: Host Flower Poker")
                                .addOption(OptionType.CHANNEL, "channel", "Select channel", true)
                                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                        Commands.slash("hc", "Staff: Host Hot Cold")
                                .addOption(OptionType.CHANNEL, "channel", "Select channel", true)
                                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                ).queue();

        System.out.println("🚀 Bot is starting with Gold Chips system...");
    }
}