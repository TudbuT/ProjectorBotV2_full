package tudbut.pbot;

import tudbut.rendering.GIFEncoder;
import tudbut.tools.Queue;
import tudbut.tools.ThreadPool;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStreamImpl;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoCoder extends Queue<byte[]> {
    
    protected int frames;
    protected File dir;
    public final int fps;
    public final int fpi;
    private final AtomicInteger currentID = new AtomicInteger();
    
    public VideoCoder(File dir, int fps, int fpi) {
        frames = (this.dir = dir).list().length;
        this.fps = fps;
        this.fpi = fpi;
    }
    
    @Override
    public byte[] next() {
        while (!hasNext());
        return super.next();
    }
    
    public void build() throws IOException {
        currentID.set(0);
        ThreadPool pool = new ThreadPool(50, "ImageGetter", false);
        while (hasNext())
            super.next();
        Map<Integer, byte[]> map = new HashMap<>();
        {
            int i = 0;
            int j = 0;
            while (i < frames) {
                int finalI = i;
                int finalJ = j;
                pool.run(() -> map.put(finalJ, buildNext(finalI)));
                i += fpi;
                j++;
            }
        }
        while (map.size() < frames / fpi) {
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.print("\r\u001b[KFrame " + currentID.get() + " (" + ((float) currentID.get() / (float) frames * 100f) + "%)                        ");
        }
        pool.stop();
        
        for (int i = 0; i < map.size(); i++) {
            add(map.get(i));
        }
    }
    
    private byte[] buildNext(int current) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            GIFEncoder encoder = new GIFEncoder(new ImageOutputStreamImpl() {
                @Override
                public int read() {
                    return 0;
                }
        
                @Override
                public int read(byte[] bytes, int i, int i1) {
                    return 0;
                }
        
                @Override
                public void write(int i) {
                    stream.write(i);
                }
        
                @Override
                public void write(byte[] bytes, int i, int i1) {
                    stream.write(bytes, i, i1);
                }
            }, BufferedImage.TYPE_USHORT_555_RGB, 1000 / fps, false);
            for (int i = 0; i < fpi && current < frames; i++, current++) {
                try {
                    encoder.addFrame(ImageIO.read(new File(dir, (current+1) + ".png")));
                }
                catch (IOException ignore) { }
                currentID.incrementAndGet();
            }
            try {
                encoder.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return stream.toByteArray();
    }
}
