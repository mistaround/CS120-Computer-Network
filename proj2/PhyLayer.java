import javax.sound.sampled.*;
import java.io.*;

public class PhyLayer
{
    private AudioFormat af = new AudioFormat(44100f, 8,
            1, true, false);
    public SourceDataLine outLine = null;
    public TargetDataLine inLine = null;
    public Receiver receiver = null;
    private Thread receiverThread;
    public Sender sender = null;
    public Thread senderThread;

    public static final int frameLen = 5;
    public static final int headerLen = 40 * 5;
    // 8 bit for pack type; 16 bit for pack #; 16 bit for pack length
    public static final int pingLen = 32 * 5;
    // do we need a buffer for PERF packs? maybe not?
    // ignore that for now.
    public static final int SEND = 0;
    public static final int ACK = 1;
    public static final int PERF = 2;
    public static final int ACK_PERF = 3;
    public static final int PING = 4;
    public static final int PING_REPLY = 5;

    public static byte[] physHeader = new byte[]{127, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    // 127 followed by 10 zeros
    public static byte[] one = new byte[]{6, 0, 0, 0, 0};
    public static byte[] zero = new byte[]{-6, 0, 0, 0, 0};
    public static byte[] physTail = new byte[2000];

    public PhyLayer()
    {

        try
        {
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            int number = 0;

            for (Mixer.Info mixerInfo : mixerInfos)
            {
                String info = mixerInfo.toString();
                if(info.contains("USB Audio") && !info.contains("Port"))
                {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    Line.Info[] tLines = mixer.getTargetLineInfo();
                    Line.Info[] sLines = mixer.getSourceLineInfo();
                    if (tLines.length != 0)
                    {
                        this.inLine = (TargetDataLine) mixer.getLine(tLines[0]);
                        System.out.printf("Identified #%d: %s as input line\n", number, info.toString());
                        inLine.open(af);
                    }
                    if (sLines.length != 0) {
                        this.outLine = (SourceDataLine) mixer.getLine(sLines[0]);
                        System.out.printf("Identified #%d: %s as output line\n", number, info.toString());
                        outLine.open(af);
                    }
                }
                number ++;
            }
            /*
            Mixer mixer = AudioSystem.getMixer(mixerInfos[6]);
            Line.Info[] tLines = mixer.getTargetLineInfo();
            Line.Info[] sLines = mixer.getSourceLineInfo();
            this.inLine = (TargetDataLine) mixer.getLine(tLines[0]);
            this.outLine = (SourceDataLine) mixer.getLine(sLines[0]);
            inLine.open(af);
            outLine.open(af);
            */
            // Judges whether in line and out line are got
            // i.e. whether USB sound card is ready
            if(this.outLine == null || this.inLine == null)
                throw new Exception("USB Sound card not found...");

            this.receiver = new Receiver(this.inLine, this);
            this.sender = new Sender(this.outLine, this);
            this.senderThread = new Thread(this.sender);
            this.senderThread.start();
            this.runReceiver(true);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private byte[] toBytes(int in, int len) throws IOException
    {
        byte[] result = new byte[len];
        for(int i=0; i<len; i++)
        {
            result[len-i-1] = (byte) (in&1);
            in >>= 1;
        }
        assert in==0;
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        for(byte i: result)
        {
            if(i == 1)
                os.write(one);
            else
                os.write(zero);
        }
        return os.toByteArray();
    }

    public void send(int type, int num, byte[] content, boolean priored)
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try
        {
            // add a header
            os.write(physHeader);
            os.write(toBytes(type, 8));
            os.write(toBytes(num, 16));
            os.write(toBytes(content.length, 16));
            for(byte p: content)
            {
                os.write(toBytes(p, 8));
            }
            os.write(physTail);
            byte[] pkg = os.toByteArray();
            if(type == PING || type == PING_REPLY)
                this.sender.vipQueue.put(pkg);
            else if(priored)
                this.sender.priorQueue.put(pkg);
            else
                this.sender.queue.put(pkg);
            //System.out.printf("Sent packet with type %d, #%d\n", type, num);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void runReceiver(boolean run)
    {
        try
        {
            if(run)
            {
                receiverThread = new Thread(this.receiver);
                receiverThread.start();
            }
            else
                this.receiver.stopped = true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

}