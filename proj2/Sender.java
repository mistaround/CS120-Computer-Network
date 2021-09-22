import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.concurrent.LinkedBlockingQueue;

public class Sender implements Runnable
{
    private SourceDataLine line;
    private PhyLayer parent;
    public LinkedBlockingQueue<byte[]> vipQueue = new LinkedBlockingQueue();
    public LinkedBlockingQueue<byte[]> priorQueue = new LinkedBlockingQueue();
    public LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue();

    public byte[] fileContent;

    public boolean stopped = false;
    public boolean warning = false;

    public Sender(SourceDataLine outLine, PhyLayer pa)
    {
        this.line = outLine;
        this.parent = pa;
        System.out.println("Preheating Microphone (for 1s)....");
        this.line.start();
        try
        {
            Thread.sleep(1000);
            System.out.println("Ready!");
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    public void readFile(String fileName)
    {
        try
        {
            FileInputStream is = new FileInputStream(fileName);
            int avail = is.available();
            this.fileContent = new byte[avail];
            is.read(this.fileContent);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void run()
    {
        try
        {
            while (!this.stopped)
            {
                // first try to analyse if priored queue has info
                if(!this.vipQueue.isEmpty())
                {
                    byte[] pkg = this.vipQueue.take();
                    int bufSize = this.line.getBufferSize();
                    // wait
                    this.line.write(pkg, 0, pkg.length);
                    System.out.println("Pinging...");
                    Thread.sleep(300);
                    this.parent.receiver.oldTimeStamp = binUtils.getTimeStamp() + (bufSize * 1000 / 44100);
                }
                else if(!this.priorQueue.isEmpty())
                {
                    byte[] pkg = this.priorQueue.take();
                    this.line.write(pkg, 0, pkg.length);
                    //this.line.drain();
                }
                else if(!this.queue.isEmpty())
                {
                    byte[] pkg = this.queue.take();
                    this.line.write(pkg, 0, pkg.length);
                    //this.line.drain();
                }
            }
            this.line.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
