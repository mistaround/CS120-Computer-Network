import java.io.*;
import java.util.Scanner;

public class Proj2
{
    public static void main(String[] args)
    {
        System.out.println("============================");
        System.out.println("Which do you want to choose?");
        System.out.println("============================");
        System.out.println(" 1. Part2 - Recv");
        System.out.println(" 2. Part2 - Send");
        System.out.println(" 3. macperf");
        System.out.println(" 4. macping");
        System.out.println("============================");

        Scanner input = new Scanner(System.in);
        int sel = input.nextInt();
        PhyLayer a = new PhyLayer();
        String fileName = "INPUT.bin";

        switch(sel)
        {
            case 1:
                a.receiver.started = false;
                try
                {
                    while (!a.receiver.allAcked())
                    {
                        Thread.sleep(200);
                    }
                    a.receiver.timer.ok = true;
                    System.out.println("Receive finished, writing to output ....");
                    a.runReceiver(false);
                    byte[] buf = a.receiver.recvContent.clone();
                    byte[] resultBuf = new byte[buf.length / 8];
                    FileOutputStream fs = new FileOutputStream("OUTPUT.bin");
                    for(int i=0; i<buf.length; i+=8)
                        resultBuf[i/8] = (byte) binUtils.getNum(i, 8, buf);
                    fs.write(resultBuf);
                    fs.flush();
                    fs.close();
                    System.out.println("Done.");

                    /*FileWriter fw = new FileWriter("test-dpkg.txt");
                    for(byte i: a.receiver.log.toByteArray())
                    {
                        fw.write(Integer.toString((int) i));
                        fw.write('\n');
                    }
                    fw.flush();
                    fw.close();*/
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                break;
            case 2:
                // open the file and read its contents
                try
                {
                    a.sender.readFile(fileName);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    for(int i=0; i<50; i++)
                    {
                        bytes.write(a.sender.fileContent, i*125, 125);
                        a.send(0, i, bytes.toByteArray(), false);
                        bytes.reset();
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                break;
            case 3:
                try
                {
                    a.sender.readFile(fileName);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    for(int i=0; i<50; i++)
                    {
                        bytes.write(a.sender.fileContent, i*125, 125);
                        a.send(PhyLayer.PERF, i, bytes.toByteArray(), false);
                        bytes.reset();
                    }
                    System.out.println("Started macperf....");
                    a.receiver.oldTimeStamp = binUtils.getTimeStamp();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                break;
            case 4:
                a.sender.warning = true;
                a.send(PhyLayer.PING, 0, new byte[0], true);
                try
                {
                    Thread.sleep(2000);
                    a.send(PhyLayer.PING, 0, new byte[0], true);
                    Thread.sleep(2000);
                    a.send(PhyLayer.PING, 0, new byte[0], true);
                    Thread.sleep(2000);
                    a.send(PhyLayer.PING, 0, new byte[0], true);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                break;
            default:
                System.out.println("Not implemented yet :(");
        }
    }
}