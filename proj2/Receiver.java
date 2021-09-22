import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;

class Receiver implements Runnable
{
    public boolean stopped = false;
    public ByteArrayOutputStream log;
    public TargetDataLine line;

    private final int thresholdHeader = 30;
    private final int thresholdOne = 60;
    private final int thresholdZero = -60;
    public int status = state.RECV;

    private int currPkgLen = 0;
    private int counter = 0;
    public byte[] headerContent = new byte[PhyLayer.headerLen];
    public byte[] recvContent = new byte[50000];
    private int writeContentFrom = 0;
    public byte[] ackedPackages = new byte[50];
    private PhyLayer parent;

    public int timeStamp = 0;
    public int oldTimeStamp = 0;
    private int bitsReceived = 0;
    public boolean started = true;
    private boolean blocked = false;
    public Timer timer;
    public boolean blockedPing = false;

    public Receiver(TargetDataLine lin, PhyLayer pa)
    {
        this.line = lin;
        this.parent = pa;
        log = new ByteArrayOutputStream();
    }

    private int recvHeader(byte[] buf)
    {
        // Try to find a header in O(n), by redesigning header.
        int beginPos = -1;
        for (int i = 0; i < 512; i++)
        {
            if (buf[i] > thresholdHeader)
            {
                beginPos = i + 10;
                return beginPos;
            }
        }
        return -1;
    }

    private int decode(byte[] buf, int offset, int beginPos, byte[] target)
    {
        int i, min, max, decd;
        byte result;
        for(i = beginPos; i < buf.length - PhyLayer.frameLen; i+= PhyLayer.frameLen)
        {
            // loop unrolled to be faster
            decd = buf[i] * 20 + buf[i+1] * 20 + buf[i+2] * 20 + buf[i+3] * 10 + buf[i+4] * 5;
            min = buf[i]; max = buf[i];

            if(buf[i+1]<min) min=buf[i+1]; if(buf[i+1]>max) max=buf[i+1];
            if(buf[i+2]<min) min=buf[i+2]; if(buf[i+2]>max) max=buf[i+2];
            if(buf[i+3]<min) min=buf[i+3]; if(buf[i+3]>max) max=buf[i+3];
            if(buf[i+4]<min) min=buf[i+4]; if(buf[i+4]>max) max=buf[i+4];

            if(decd > thresholdOne)
                result = 1;
            else if(decd < thresholdZero)
                result = 0;
            else if(max > -min)
                result = 1;
            else if(max < -min)
                result = 0;
            else
            {
                // may have error here
                System.out.println(" --- Link Error!!!! ---");
                System.out.printf("Unexpected end of decoding at %d\n", counter);
                System.out.printf("Error at: %d, %d, %d, %d, %d\n", buf[i], buf[i+1], buf[i+2], buf[i+3], buf[i+4]);
                return -2;
            }
            target[counter + writeContentFrom] = result;
            counter++;
            if(counter >= currPkgLen)
            {
                //if(this.currPkgLen != 40)
                 //   System.out.printf("Safely received %d bytes of data.\n", currPkgLen);
                return -1;
            }
        }
        return i - offset;
    }

    public boolean allAcked()
    {
        for(int i=0; i<this.ackedPackages.length; i++)
            if(this.ackedPackages[i] == 0)
                return false;
        return true;
    }

    private boolean editAttributes(int pkgType, int pkgNum, int pkgLen)
    {
        // return value: true -> no need to decode pkg type
        //               false -> otherwise
        if(pkgNum >= 50 || pkgLen > 1000)
            return true; // this pack is invalid
        switch (pkgType)
        {
            case PhyLayer.SEND:
                this.parent.send(PhyLayer.ACK, pkgNum, new byte[0], false);
                this.writeContentFrom = 1000 * pkgNum;
                this.currPkgLen = pkgLen * 8;
                this.ackedPackages[pkgNum] = 1;
                System.out.printf("Received data pack #%d length %d\n", pkgNum, currPkgLen);
                return false;
            case PhyLayer.ACK:
                if(this.ackedPackages[pkgNum] == 1) return true;
                // the above is used to solve unknown problem
                System.out.printf("Received ACK for pack %d\n", pkgNum);
                this.ackedPackages[pkgNum] = 1;
                for(int i=0; i<pkgNum; i++)
                    if(this.ackedPackages[i] == 0)
                    {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        this.ackedPackages[i] = 1;
                        bytes.write(this.parent.sender.fileContent, i*125, 125);
                        this.parent.send(0, i, bytes.toByteArray(), false);
                        bytes.reset();
                        break;
                    }
                return true;
            case PhyLayer.PERF:
                this.parent.send(PhyLayer.ACK_PERF, pkgNum, new byte[0], true);
                this.writeContentFrom = 1000 * pkgNum;
                this.currPkgLen = pkgLen * 8;
                this.ackedPackages[pkgNum] = 1;
                return false;
            case PhyLayer.ACK_PERF:
                if(this.blocked) return true;
                int elapsed = binUtils.getTimeStamp() - this.oldTimeStamp;
                this.ackedPackages[pkgNum] = 1;
                this.bitsReceived += 1;
                if(this.bitsReceived == 50) this.blocked = true;
                System.out.printf("Received data: %dkbits, time elapsed: %dms, bandwidth: %dbps\n",
                    this.bitsReceived, elapsed, (1000000 * this.bitsReceived / elapsed));
                return true;
            case PhyLayer.PING:
                if(this.blockedPing) return true;
                this.parent.send(PhyLayer.PING_REPLY, pkgNum, new byte[0], true);
                this.blockedPing = true;
                return true;
            case PhyLayer.PING_REPLY:
                System.out.printf("Ping finished. RTT = %dms.\n", this.timeStamp - this.oldTimeStamp);
                this.blockedPing = true;
                this.parent.sender.warning = false;
                return true;
            default:
                break;
        }
        return false;
    }

    public void run()
    {
        byte[] buf = new byte[512];
        ByteArrayOutputStream lastOut = new ByteArrayOutputStream();
        int nextTurnToRun = -1;
        try
        {
            line.start();
            while (!this.stopped)
            {
                this.timeStamp = binUtils.getTimeStamp() - (512 * 1000 / 44100);
                line.read(buf, 0, buf.length);
                int beginPos = -1;
                if(this.status == state.RECV)
                {
                    beginPos = this.recvHeader(buf);
                    if(beginPos != -1)
                    {
                        this.status = state.RX;
                        //System.out.println("Begin decoding!!");
                        if(!this.started)
                        {
                            timer = new Timer(10);
                            Thread th = new Thread(timer);
                            th.start();
                            this.started = true;
                        }
                    }
                    if(beginPos >= 512)
                    {
                        nextTurnToRun = beginPos - 512; // begin RX in next turn.
                        //System.out.println("---1---");
                        continue;
                    }
                    else if(beginPos + PhyLayer.headerLen >= 512)
                    {
                        // also begin RX in next turn, but save data
                        nextTurnToRun = 0;
                        // push everything after [beginPos] to stream
                        lastOut.write(buf, beginPos, buf.length - beginPos);
                        //System.out.println("---2---");
                        continue;
                    }
                    // else, both do RX and TX in this turn.
                }
                if(this.status == state.RX) // may be transferred from RECV to RX
                {
                    this.currPkgLen = PhyLayer.headerLen / 5;
                    this.writeContentFrom = 0;
                    this.counter = 0;
                    if(nextTurnToRun == -1)
                    {
                        // recv, tx and rx in the same frame; start from the current "beginPos".
                        decode(buf, 0, beginPos, headerContent);
                        //System.out.println("---3---");
                        beginPos += PhyLayer.headerLen;
                    }
                    else if(nextTurnToRun == 0)
                    {
                        // start from lastOut
                        int size = lastOut.size();
                        lastOut.write(buf);
                        decode(lastOut.toByteArray(), 0, 0, headerContent);
                        lastOut.reset();
                        // the following is used when Rx is split
                        beginPos = PhyLayer.headerLen - size;
                        //System.out.println("---4---");
                    }
                    else // start from nTTR
                    {
                        decode(buf, 0, nextTurnToRun, headerContent);
                        beginPos = nextTurnToRun + PhyLayer.headerLen;
                        //System.out.println("---5---");
                    }
                    nextTurnToRun = -1;
                    // set proper attributes for TX.
                    int pkgType = binUtils.getNum(0, 8, this.headerContent);
                    int pkgNum = binUtils.getNum(8, 16, this.headerContent);
                    int pkgLen = binUtils.getNum(24, 16, this.headerContent);
                    // set attributes.
                    boolean skip = this.editAttributes(pkgType, pkgNum, pkgLen);
                    counter = 0;
                    if(skip)
                        this.status = state.RECV;
                    else
                        this.status = state.TX;
                }
                if(this.status == state.TX)
                {
                    int finalPos = 0;
                    // do depkg according to beginPos.
                    if(beginPos == -1) // last turn TX -> Curr turn TX
                    {
                        // check buffer, and continue from buffer.
                        if(nextTurnToRun != -1)
                        {
                            System.out.println("---6---");
                            finalPos = decode(buf, 0, nextTurnToRun, recvContent);
                            nextTurnToRun = -1;
                        }
                        else
                        {
                            //System.out.println("---7---");
                            int offs = lastOut.size();
                            lastOut.write(buf);
                            finalPos = decode(lastOut.toByteArray(), offs, 0, recvContent);
                        }
                    }
                    else // this turn, FD -> RX -> TX
                    {
                        //System.out.println("---8---");
                        int offs = lastOut.size();
                        lastOut.write(buf);
                        finalPos = decode(lastOut.toByteArray(), offs, beginPos, recvContent);
                    }
                    // deal with the left numbers in TX
                    lastOut.reset();
                    if(finalPos > 0)
                        lastOut.write(buf, finalPos, buf.length - finalPos);
                    else if(finalPos == -1)
                    {
                        this.status = state.RECV;
                        continue;
                        //this.stopped = true;
                    }
                    else if(finalPos == -2)
                    {
                        this.stopped = true;
                        // don't know what to do
                    }
                }
                if(this.started)
                    log.write(buf);
            }
            line.stop();
            this.stopped = true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        try
        {
            FileWriter fw = new FileWriter("test-dpkg.txt");
            for(byte i: log.toByteArray())
            {
                fw.write(Integer.toString((int) i));
                fw.write('\n');
            }
            fw.flush();
            fw.close();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }
}