package tudbut.pbot;

import de.tudbut.io.StreamReader;
import de.tudbut.io.StreamWriter;
import jdk.nashorn.internal.parser.TokenType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import tudbut.tools.Queue;
import tudbut.tools.Tools2;

import javax.imageio.ImageIO;
import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;

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
                    p = Runtime.getRuntime().exec("ffmpeg -r 30 -i vid.mp4 -vf fps=fps=30,scale=240:180,setsar=1:1 vid/%0d.png");
                    while (p.isAlive());
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Converting to compatible frames...");
                DecoderEncoder encoder = new DecoderEncoder(new File("vid"), 30, 5 * 30);
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
                            byte[] bytes;
                            Queue<byte[]> queue = new Queue<>();
                            for (int i = 0; i < all.size(); i++) {
                                queue.add(all.get(i));
                            }
                            bytes = queue.next();
                            long sa = new Date().getTime();
                            Message message = event.getMessage().getChannel().sendMessage("Image will appear below").addFile(bytes, "generated.gif").complete();
                            try {
                                Thread.sleep(5000 - (new Date().getTime() - sa));
                            }
                            catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            while (queue.hasNext()) {
                                sa = new Date().getTime();
                                bytes = queue.next();
                                Message n = message;
                                if (
                                        n.getChannel().getHistory().retrievePast(10).complete().stream().anyMatch(
                                                msg -> msg.getIdLong() == n.getIdLong()
                                        )
                                ) {
                                    if(
                                        n.getChannel().getHistory().retrievePast(10).complete().stream().noneMatch(
                                                msg -> {
                                                    boolean b = msg.getContentDisplay().equalsIgnoreCase("!stop");
                                                    if(b)
                                                        msg.delete().queue();
                                                    return b;
                                                }
                                        )
                                    ) {
                                        message = message.getChannel().sendMessage("Image will appear below").addFile(bytes, "generated.gif").complete();
                                        n.delete().queue();
                                    } else {
                                        n.delete().complete();
                                        return;
                                    }
                                } else {
                                    return;
                                }
                                try {
                                    Thread.sleep(5000 - (new Date().getTime() - sa));
                                }
                                catch (Exception e) {
                                    message.delete().queue();
                                    return;
                                }
                            }
                            message.delete().queue();
                        }).start();
                    }
                }
                
            }
        });
    }
}
