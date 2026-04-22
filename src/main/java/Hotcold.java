import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.awt.Color;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Hotcold extends ListenerAdapter {

    private static final Map<String, String> FLOWERS = Map.of(
            "red", "<:red:1496424239450820608>",
            "orange", "<:orange:1496424381537194175>",
            "yellow", "<:yellow:1496424271403290806>",
            "blue", "<:blue:1496424324066971668>",
            "assorted", "<:assorted:1496424438953017375>",
            "purple", "<:purple:1496424408737251328>",
            "rainbow", "<:mixed:1496424200288862260>"
    );

    private String hostId = null;
    private Message lastBettingMessage = null;
    private boolean isBettingOpen = false;
    private boolean isProcessing = false;
    private boolean isTimerStarted = false;
    private final List<Bet> currentBets = Collections.synchronizedList(new ArrayList<>());
    private final LinkedList<String> streak = new LinkedList<>();

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("hc")) {
            hostId = event.getUser().getId();
            event.reply("🔥 **HotCold Session Started!** Check the selected channel.").setEphemeral(true).queue();
            startNewRound(event.getOption("channel").getAsChannel().asTextChannel());
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id.startsWith("hc_bet_")) {
            if (!isBettingOpen) { event.reply("❌ This round has already ended!").setEphemeral(true).queue(); return; }
            
            String userId = event.getUser().getId();
            synchronized (currentBets) {
                for (Bet b : currentBets) {
                    if (b.userId.equals(userId)) {
                        event.reply("❌ You already have an active bet! You can only pick **one** side.").setEphemeral(true).queue();
                        return;
                    }
                }
            }

            event.replyModal(Modal.create("m_hc_" + id.split("_")[2], "Place Your Bet")
                    .addActionRows(ActionRow.of(TextInput.create("amt", "Amount (M)", TextInputStyle.SHORT).setPlaceholder("Example: 100 or 100m").build())).build()).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().startsWith("m_hc_")) {
            try {
                String side = event.getModalId().split("_")[2];
                String input = event.getValue("amt").getAsString().toLowerCase().replace("m", "").trim();
                double amt = Double.parseDouble(input);
                String userId = event.getUser().getId();

                if (amt < 0.01) { event.reply("❌ Minimum bet is 0.01M").setEphemeral(true).queue(); return; }

                UserData ud = Database.getUserData(userId);
                if (ud.balance < amt) { event.reply("❌ Insufficient Balance!").setEphemeral(true).queue(); return; }

                ud.balance -= amt;
                Database.saveUserData(userId, ud);
                Database.updateWagerAndRakeback(userId, amt);

                currentBets.add(new Bet(userId, side, amt));

                event.reply(String.format("✅ **Bet Accepted!** `%.2fM` on `%s`", amt, side.toUpperCase()))
                        .setEphemeral(true).queue();

                if (!isTimerStarted) startCountdown(event.getChannel().asTextChannel());
                else updateLiveEmbed(event.getChannel().asTextChannel()); // Update UI to show new player

            } catch (Exception e) { event.reply("❌ Invalid Amount! Use numbers only.").setEphemeral(true).queue(); }
        }
    }

    private void startNewRound(TextChannel channel) {
        isBettingOpen = true; isProcessing = false; isTimerStarted = false;
        currentBets.clear();
        sendBettingEmbed(channel, false);
    }

    private void startCountdown(TextChannel channel) {
        isTimerStarted = true;
        sendBettingEmbed(channel, true);
        Main.scheduler.schedule(() -> {
            if (isBettingOpen) { isBettingOpen = false; processResults(channel); }
        }, 30, TimeUnit.SECONDS);
    }

    private void updateLiveEmbed(TextChannel channel) {
        sendBettingEmbed(channel, true);
    }

    private void processResults(TextChannel channel) {
        if (isProcessing) return;
        isProcessing = true;

        String rolled;
        Random r = new Random();
        int chance = r.nextInt(100);

        if (chance < 5) rolled = "rainbow"; // 5% chance for Rainbow
        else {
            String[] hotColdFlowers = {"red", "orange", "yellow", "blue", "assorted", "purple"};
            rolled = hotColdFlowers[r.nextInt(hotColdFlowers.length)];
        }

        String winningSide = determineSide(rolled);

        synchronized (streak) {
            if (streak.size() >= 8) streak.removeFirst();
            streak.add(FLOWERS.get(rolled));
        }

        StringBuilder winners = new StringBuilder();
        StringBuilder losers = new StringBuilder();
        double totalPayout = 0;

        List<Bet> betsToProcess;
        synchronized (currentBets) { betsToProcess = new ArrayList<>(currentBets); }

        for (Bet b : betsToProcess) {
            UserData ud = Database.getUserData(b.userId);
            if (b.side.equals(winningSide)) {
                double mult = winningSide.equals("rainbow") ? 12.0 : (winningSide.equals("hot") ? 2.0 : 2.1);
                double winAmt = b.amount * mult;
                ud.balance += winAmt;
                Database.saveUserData(b.userId, ud);
                winners.append("<@").append(b.userId).append("> +`").append(String.format("%.2f", winAmt)).append("M`\n");
                totalPayout += winAmt;
            } else {
                losers.append("<@").append(b.userId).append("> -`").append(String.format("%.2f", b.amount)).append("M`\n");
            }
        }

        EmbedBuilder rb = new EmbedBuilder()
                .setAuthor("Hot Cold Result", null, channel.getGuild().getIconUrl())
                .setTitle(winningSide.toUpperCase() + " WINS!", null)
                .setColor(winningSide.equals("hot") ? Color.RED : (winningSide.equals("cold") ? Color.BLUE : Color.PINK))
                .setDescription("The flower rolled is: " + FLOWERS.get(rolled) + " **" + rolled.toUpperCase() + "**")
                .addField("🏆 Winners", winners.length() == 0 ? "None" : winners.toString(), true)
                .addField("💀 Losers", losers.length() == 0 ? "None" : losers.toString(), true)
                .setFooter("Total Payout: " + String.format("%.2fM", totalPayout))
                .setTimestamp(Instant.now());

        if (lastBettingMessage != null) lastBettingMessage.delete().queue(null, t -> {});

        channel.sendMessageEmbeds(rb.build()).queue(s -> 
            Main.scheduler.schedule(() -> startNewRound(channel), 7, TimeUnit.SECONDS)
        );
    }

    private void sendBettingEmbed(TextChannel channel, boolean withTimer) {
        String streakStr = streak.isEmpty() ? "No history yet" : String.join(" ", streak);
        String timeDisplay = withTimer ? "Ends " + String.format("<t:%d:R>", (System.currentTimeMillis() / 1000) + 30) : "Waiting for bets...";

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("🔥 HOT COLD ❄️")
                .setColor(new Color(255, 182, 193))
                .setDescription("Bet on which color group will be rolled!")
                .addField("⏱️ Time Remaining", timeDisplay, false)
                .addField("📊 History (Last 8)", streakStr, false)
                .addField("💰 Multipliers", "🔥 **Hot**: `x2.0` (Red, Orange, Yellow)\n❄️ **Cold**: `x2.1` (Blue, Purple, Assorted)\n🌈 **Rainbow**: `x12.0` (Rare Mixed)", false);

        // Active players count
        if (!currentBets.isEmpty()) {
            eb.addField("🎮 Active Players", "Current Bets: `" + currentBets.size() + "`", true);
        }

        eb.setFooter("Click the buttons below to join!");

        if (withTimer && lastBettingMessage != null) {
            lastBettingMessage.editMessageEmbeds(eb.build()).queue(null, t -> createNewBettingMessage(channel, eb));
        } else {
            if (lastBettingMessage != null) lastBettingMessage.delete().queue(null, t -> {});
            createNewBettingMessage(channel, eb);
        }
    }

    private void createNewBettingMessage(TextChannel channel, EmbedBuilder eb) {
        channel.sendMessageEmbeds(eb.build()).addActionRow(
                Button.danger("hc_bet_hot", "Hot (x2.0)"),
                Button.primary("hc_bet_cold", "Cold (x2.1)"),
                Button.success("hc_bet_rainbow", "Rainbow (x12.0)")
        ).queue(msg -> lastBettingMessage = msg);
    }

    private String determineSide(String flower) {
        if (flower.equals("red") || flower.equals("orange") || flower.equals("yellow")) return "hot";
        if (flower.equals("blue") || flower.equals("assorted") || flower.equals("purple")) return "cold";
        return "rainbow";
    }

    private static class Bet { String userId, side; double amount; Bet(String u, String s, double a) { userId=u; side=s; amount=a; } }
}
