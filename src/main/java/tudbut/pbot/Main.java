package tudbut.pbot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEvent;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import de.tudbut.io.StreamReader;
import de.tudbut.io.StreamWriter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jetbrains.annotations.NotNull;
import tudbut.tools.Queue;
import tudbut.tools.Tools2;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    static volatile boolean done = false;
    
    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        JDA jda = JDABuilder.createLight(args[0]).build();
        ArrayList<byte[]> all = new ArrayList<>();
        
        jda.awaitReady();
        if(Files.exists(Paths.get("vid_encoded"))) {
            File dir = new File("vid_encoded");
            int frames = dir.list().length;
            for (int i = 0; i < frames; i++) {
                all.add(new StreamReader(new FileInputStream(new File(dir, i + ""))).readAllAsBytes());
            }
            done = true;
        }
        else {
            new Thread(() -> {
                new File("vid").mkdir();
                System.out.println("Converting to raw frames...");
                try {
                    Process p;
                    p = Runtime.getRuntime().exec("ffmpeg -i vid.mp4 -vf fps=fps=30 vid_30fps.mp4");
                    while (p.isAlive());
                    p = Runtime.getRuntime().exec("ffmpeg -i vid_30fps.mp4 -vf scale=240:180,setsar=1:1 vid/%0d.png");
                    while (p.isAlive());
                    new File("vid_30fps.mp4").delete();
                    p = Runtime.getRuntime().exec("ffmpeg -i vid.mp4 aud.opus");
                    while (p.isAlive());
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Converting to compatible frames...");
                File aud = new File("aud_encoded");
                Tools2.deleteDir(aud);
                aud.delete();
                new File("aud.opus").renameTo(aud);
                VideoCoder encoder = new VideoCoder(new File("vid"), 30, 5 * 30);
                try {
                    encoder.build();
                    while (encoder.hasNext())
                        all.add(encoder.next());
                    done = true;
                    System.out.println("\nDeleting old data...");
                    Tools2.deleteDir(new File("vid"));
                    System.out.println("Saving state for next usage... To use a new video, delete the vid_encoded directory!");
                    new File("vid_encoded").mkdir();
                    for (int i = 0; i < all.size(); i++) {
                        File file = new File("vid_encoded/" + i);
                        file.createNewFile();
                        if(all.get(i) != null)
                            new StreamWriter(new FileOutputStream(file)).writeBytes(all.get(i));
                    }
                    System.out.println("DONE!");
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
        jda.addEventListener(new EventListener() {
            @Override
            public void onEvent(@NotNull GenericEvent event) {
                if(event instanceof MessageReceivedEvent)
                    onMessage((MessageReceivedEvent) event);
            }
    
            @SubscribeEvent
            public void onMessage(MessageReceivedEvent event) {
                String s = event.getMessage().getContentDisplay();
                //System.out.println(s);
                if(s.equalsIgnoreCase("!play")) {
                    //System.out.println("Playing...");
                    if(all.isEmpty() || !done) {
                        event.getMessage().getChannel().sendMessage("Calculation not done.").complete();
                    }
                    else {
                        new Thread(() -> {
                            try {
                                event.getMessage().getChannel().sendMessage("*<VideoBot by TudbuT#2624>* Starting up...").complete();
                                Member member = event.getMessage().getMember();
                                AtomicBoolean lock = new AtomicBoolean();
                                VoiceChannel vc = null;
    
                                byte[] bytes;
                                Queue<byte[]> queue = new Queue<>();
                                for (int i = 0; i < all.size(); i++) {
                                    queue.add(all.get(i));
                                }
                                
                                if (member != null) {
                                    vc = member.getGuild().createVoiceChannel("VideoBot-Sound").complete();
                                    if (vc != null) {
                                        event.getMessage().getChannel().sendMessage("*<VideoBot by TudbuT#2624>* Starting voice emulator...").complete();
                                        AudioManager manager = event.getGuild().getAudioManager();
                                        VoiceChannel finalVc = vc;
                                        AudioEventAdapter listener = new AudioEventAdapter() {
                                            @Override
                                            public void onEvent(AudioEvent event) {
                                            
                                            }
    
                                            @Override
                                            public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                                                manager.closeAudioConnection();
                                                finalVc.delete().queue();
                                            }
                                        };
                                        DefaultAudioPlayerManager playerManager = new DefaultAudioPlayerManager();
                                        AudioPlayer player = playerManager.createPlayer();
                                        player.addListener(listener);
                                        playerManager.registerSourceManager(new LocalAudioSourceManager());
                                        playerManager.loadItem(new File("aud_encoded").getAbsolutePath(), new AudioLoadResultHandler() {
                                            @Override
                                            public void trackLoaded(AudioTrack track) {
                                                manager.setSendingHandler(new AudioCoder(player));
                                                manager.openAudioConnection(finalVc);
                                                while (!lock.get());
                                                lock.set(false);
                                                player.playTrack(track);
                                                while (track.getState() != AudioTrackState.PLAYING);
                                                lock.set(true);
                                            }
    
                                            @Override
                                            public void playlistLoaded(AudioPlaylist playlist) {
        
                                            }
    
                                            @Override
                                            public void noMatches() {
                                                System.out.println("File not found: aud_encoded! Make sure it exists or delete vid_encoded too for it to be remade.");
                                                manager.closeAudioConnection();
                                                finalVc.delete().queue();
                                            }
    
                                            @Override
                                            public void loadFailed(FriendlyException exception) {
                                                manager.closeAudioConnection();
                                                finalVc.delete().queue();
                                            }
                                        });
                                    }
                                    else
                                        event.getMessage().getChannel().sendMessage("*<VideoBot by TudbuT#2624>* Couldn't start audio! You are not in a VC and I couldn't create one!").complete();
                                }
                                else
                                    event.getMessage().getChannel().sendMessage("*<VideoBot by TudbuT#2624>* Couldn't start audio! We are in DMs!").complete();
                                event.getMessage().getChannel().sendMessage("*<VideoBot by TudbuT#2624>* Starting video...").complete();
    
                                long sa;
                                Message message = event.getMessage().getChannel().sendMessage("*<VideoBot by TudbuT#2624>* Image will appear below").complete();
                                sa = new Date().getTime();
                                while (queue.hasNext()) {
                                    bytes = queue.next();
                                    Message n = message;
                                    if (
                                            n.getChannel().getHistory().retrievePast(10).complete().stream().anyMatch(
                                                    msg -> msg.getIdLong() == n.getIdLong()
                                            )
                                    ) {
                                        if (
                                                n.getChannel().getHistory().retrievePast(10).complete().stream().noneMatch(
                                                        msg -> {
                                                            boolean b = msg.getContentDisplay().equalsIgnoreCase("!stop");
                                                            if (b)
                                                                msg.delete().queue();
                                                            return b;
                                                        }
                                                )
                                        ) {
                                            message = message.getChannel().sendMessage("*<VideoBot by TudbuT#2624>* Image will appear below").addFile(bytes, "generated.gif").complete();
                                            n.delete().queue();
                                            lock.set(true);
                                        }
                                        else {
                                            if (vc != null) {
                                                vc.delete().queue();
                                            }
                                            n.delete().complete();
                                            return;
                                        }
                                    }
                                    else {
                                        return;
                                    }
                                    try {
                                        Thread.sleep(5000 - (new Date().getTime() - sa));
                                    }
                                    catch (Exception e) {
                                        if (vc != null) {
                                            vc.delete().queue();
                                        }
                                        message.delete().queue();
                                        return;
                                    }
                                    sa = new Date().getTime();
                                }
                                if (vc != null) {
                                    vc.delete().queue();
                                }
                                message.delete().queue();
                            } catch (PermissionException e) {
                                try {
                                    event.getMessage().getChannel().sendMessage("*<VideoBot by TudbuT#2624>* Missing permissions!");
                                } catch (PermissionException ignore) { }
                            }
                        }).start();
                    }
                }
                
            }
        });
    }
}
