import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.util.Arrays;

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
    private int MacCounter = 0;
    public byte[] headerContent = new byte[PhyLayer.headerLen];
    //public byte[] recvContent = new byte[5000];
    public byte[][] currContent = new byte[31][5000];
    public int currPackNum = 0;
    public boolean currSent = false;
    public long Dest = 0;
    public long Src = 0;

    private int writeContentFrom = 0;
    //TODO
    //public byte[] ackedPackages = new byte[10];
    private PhyLayer parent;

    //public int timeStamp = 0;
    public int oldTimeStamp = 0;
    //private int bitsReceived = 0;
    public boolean started = true;
    //private boolean blocked = false;
    //public Timer timer;
    //public boolean blockedPing = false;

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
        for (int i = 0; i < 2048; i++)
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
            counter ++;
            MacCounter ++;
            if(MacCounter >= currPkgLen)
            {
                currSent = true; // Sent signal to main
//                int in = 8*((counter + writeContentFrom)/8);
//                int out = in + 8*((counter + writeContentFrom - in)/8);
//                byte[] tmp = Arrays.copyOfRange(currContent,0,out);

                return -1;
            }
        }
        return i - offset;
    }

    private void decodeForHeader(byte[] buf, int offset, int beginPos, int len)
    {
        int i, min, max, decd;
        int count = 0;
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
                System.out.println(" --- Header Decode Error!!!! ---");
                System.out.printf("Unexpected end of decoding at %d\n", count);
                return;
            }
            headerContent[count] = result;
            count ++;
            if(count >= len)
                return;
        }
    }

    private void decoderForMac(byte[] buf, byte[] target)
    {
        int i, min, max, decd;
        byte result;
        for(i = 0; i < buf.length - PhyLayer.frameLen; i+= PhyLayer.frameLen)
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
                System.out.println(" --- MAC Decode Error!!!! ---");
                System.out.printf("Unexpected end of decoding at %d\n", i);
                return;
            }
            target[i/PhyLayer.frameLen] = result;
        }
    }

//    public boolean allAcked()
//    {
//        for(int i=0; i<this.ackedPackages.length; i++)
//            if(this.ackedPackages[i] == 0)
//                return false;
//        return true;
//    }

    private boolean editAttributes(int pkgType, int pkgNum, int pkgLen)
    {
        // return value: true -> no need to decode pkg type
        //               false -> otherwise
        //if(pkgNum >= 50 || pkgLen > 1000)
            //return true; // this pack is invalid
        switch (pkgType)
        {
            case PhyLayer.SEND:
                //this.parent.macLayer.send(PhyLayer.ACK, pkgNum, new byte[0], false);
                // TODO test
                //this.writeContentFrom = pkgLen * pkgNum;
                this.currPackNum = pkgNum;
                this.currPkgLen = pkgLen * 8;
                //this.ackedPackages[pkgNum] = 1;
                System.out.println();
                System.out.printf("Received data pack #%d length %d bits\n", pkgNum, currPkgLen);
                return false;
            default:
                break;
        }
        return false;
    }

    public void run()
    {
        byte[] buf = new byte[2048];
        ByteArrayOutputStream lastOut = new ByteArrayOutputStream();
        ByteArrayOutputStream lastMAC = new ByteArrayOutputStream();
        MACLayer utilMAC = new MACLayer(hostIP.NODE1A, hostIP.NODE2A, parent);
        int nextTurnToRun = -1;
        try
        {
            line.start();
            while (!this.stopped)
            {
                //this.timeStamp = binUtils.getTimeStamp() - (2048 * 1000 / 44100);
                line.read(buf, 0, buf.length);
                int beginPos = -1;

                if(this.status == state.RECV)
                {
                    beginPos = this.recvHeader(buf);
                    if(beginPos != -1)
                    {
                        this.status = state.RX;
                        //System.out.println("Begin decoding!!");
                        // TODO: delete
                        /*
                        if(!this.started)
                        {
                            timer = new Timer(10);
                            Thread th = new Thread(timer);
                            th.start();
                            this.started = true;
                        }

                         */
                    }
                    if(beginPos >= 2048)
                    {
                        nextTurnToRun = beginPos - 2048; // begin RX in next turn.
                        //System.out.println("---1---");
                        continue;
                    }
                    else if(beginPos + PhyLayer.headerLen >= 2048)
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
                    //this.currPkgLen = PhyLayer.headerLen / 5;


                    this.writeContentFrom = 0;
                    this.counter = 0;
                    this.MacCounter = 0;

                    if(nextTurnToRun == -1)
                    {
                        // recv, tx and rx in the same frame; start from the current "beginPos".
                        decodeForHeader (buf, 0, beginPos, PhyLayer.headerLen / 5);
                        //System.out.println("---3---");
                        beginPos += PhyLayer.headerLen;
                    }
                    else if(nextTurnToRun == 0)
                    {
                        // start from lastOut
                        int size = lastOut.size();
                        lastOut.write(buf);
                        decodeForHeader (lastOut.toByteArray(), 0, 0, PhyLayer.headerLen / 5);
                        lastOut.reset();
                        // the following is used when Rx is split
                        beginPos = PhyLayer.headerLen - size;
                        //System.out.println("---4---");
                    }
                    else // start from nTTR
                    {
                        decodeForHeader (buf, 0, nextTurnToRun, PhyLayer.headerLen / 5);
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
                    //counter = 0;
                    //MacCounter = 0;
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
                        // If MAC Header out of buffer
                        if(nextTurnToRun != -1)
                        {
                            //System.out.println("---6---");
                            //TODO memory for recvContent
                            //utilMAC.UnPackPhy(buf, nextTurnToRun);
                            //utilMAC.printHost();
                            lastMAC.write(buf,0,nextTurnToRun);
                            byte[] info = new byte[MACLayer.MAC_HEADER_LEN];
                            decoderForMac(lastMAC.toByteArray(),info);
                            byte[] resultBuf = new byte[info.length / 8];
                            for(int j=0; j<info.length; j+=8)
                                resultBuf[j/8] = (byte) binUtils.getNum(j, 8, info);
                            utilMAC.UnPackPhy(resultBuf,0);
                            utilMAC.printHost();
                            Dest = utilMAC.Dest;
                            Src = utilMAC.Src;
                            lastMAC.reset();

                            finalPos = decode(buf, 0, nextTurnToRun, currContent[currPackNum]);

                            nextTurnToRun = -1;
                        }
                        else
                        {
                            //System.out.println("---7---");
                            //utilMAC.UnPackPhy(buf, );
                            //utilMAC.printHost();
                            int offs = lastOut.size();
                            //byte[] tmp = Arrays.copyOfRange(buf, PhyLayer.headerLen, buf.length);
                            //utilMAC.UnPackPhy(tmp, 0);
                            //utilMAC.printHost();
                            //tmp = Arrays.copyOfRange(tmp, MACLayer.MAC_HEADER_LEN * 5, tmp.length);
                            lastOut.write(buf);
                            //TODO memory for recvContent
                            finalPos = decode(lastOut.toByteArray(), offs, 0, currContent[currPackNum]);
                        }
                    }
                    else // this turn, FD -> RX -> TX
                    {
                        //System.out.println("---8---");
                        // Mac Header kick out
                        if (beginPos + MACLayer.MAC_HEADER_LEN * 5 > buf.length)
                        {
                            nextTurnToRun = beginPos + MACLayer.MAC_HEADER_LEN * 5 - buf.length;
                            MacCounter += MACLayer.MAC_HEADER_LEN;
                            lastMAC.write(buf,beginPos,buf.length - beginPos);
                        }
                        else
                        {
                            byte[] MAC = Arrays.copyOfRange(buf, beginPos,beginPos + MACLayer.MAC_HEADER_LEN * 5);
                            byte[] info = new byte[MACLayer.MAC_HEADER_LEN];
                            decoderForMac(MAC,info);
                            byte[] resultBuf = new byte[info.length / 8];
                            for(int j=0; j<info.length; j+=8)
                                resultBuf[j/8] = (byte) binUtils.getNum(j, 8, info);
                            utilMAC.UnPackPhy(resultBuf,0);
                            utilMAC.printHost();
                            Dest = utilMAC.Dest;
                            Src = utilMAC.Src;

                            beginPos += MACLayer.MAC_HEADER_LEN * 5;
                            //TODO memory for recvContent
//                            int in = 8*((counter + writeContentFrom)/8);
                            MacCounter += MACLayer.MAC_HEADER_LEN;
                            finalPos = decode(buf, 0, beginPos, currContent[currPackNum]);
                        }
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