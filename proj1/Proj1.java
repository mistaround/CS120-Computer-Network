import javax.sound.sampled.*;
import java.util.Scanner;
import java.io.*;

class Timer implements Runnable
{
	public boolean isTimeUp;
	protected int time;

	public Timer(int time)
	{
		this.time = time;
		this.isTimeUp = false;
	}

	public void run()
	{
		System.out.println("Start countdown.....");
		for(int i=this.time; i > 0; i--)
		{
			System.out.println("Time left: " + i + " sec....");
			try
			{
				Thread.sleep(1000);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}	
		this.isTimeUp = true;
	}
}

class MusicPlayer implements Runnable
{
	protected SourceDataLine line;
	protected AudioInputStream audio;
	protected boolean stopped;

	public MusicPlayer(Scanner input)
	{
		stopped = false;
		try
		{
			System.out.println("Please select output device:");
			int a = input.nextInt();
			audio = AudioSystem.getAudioInputStream(new File("test.wav"));
			AudioFormat af = audio.getFormat();
			System.out.println(af.toString());
			Mixer out = AudioSystem.getMixer(AudioSystem.getMixerInfo()[a]);
			line = (SourceDataLine) out.getLine(out.getSourceLineInfo()[0]);
			//line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, af));
			line.open(af);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
	}

	public void run()
	{
		try
		{
			line.start();

			byte[] buf = new byte[512];
			int readBytes = 0;
			while(!stopped && readBytes != -1)
			{
				readBytes = audio.read(buf, 0, buf.length);
				if(readBytes > 0)
				{
					line.write(buf, 0, readBytes);
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public void stop()
	{
		stopped = true;
		line.stop();
		line.close();
	}
}


class Task1
{
	public Task1(boolean playSound, boolean dump)
	{
		AudioFormat af = new AudioFormat(44100f, 16, 1, true, false);
		System.out.println(af.toString());

		System.out.println("Which device to input? Enter a number");
		Scanner input = new Scanner(System.in);
		int number = input.nextInt();

		// get the corresponding mixer.
		Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
		Mixer mixerIn = AudioSystem.getMixer(mixerInfos[number]);
		Line.Info[] lineInfosIn = mixerIn.getTargetLineInfo();

		TargetDataLine lineIn;
		ByteArrayOutputStream record = new ByteArrayOutputStream();
		try
		{
			lineIn = (TargetDataLine) mixerIn.getLine(lineInfosIn[0]);
			
			System.out.println("Buffer size: " + lineIn.getBufferSize());
			lineIn.open(af);

			byte[] buffer = new byte[lineIn.getBufferSize() / 5];
			Timer timer;
			// if dump, then record for 15s.
			if(!dump)
				timer = new Timer(10);
			else
				timer = new Timer(15);
			Thread timerThread = new Thread(timer);
			MusicPlayer player = new MusicPlayer(input);

			if(playSound)
			{
				Thread playerThread = new Thread(player);
				playerThread.start();
			}
			lineIn.start();
			timerThread.start();
			while(!timer.isTimeUp)
			{
				int bytes = lineIn.read(buffer, 0, buffer.length);
				record.write(buffer, 0, bytes);
			}
			if(playSound)
			{
				player.stop();
			}
			lineIn.stop();
			lineIn.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		if(dump) //
		{
			byte[] outBuffer = record.toByteArray();

			try
			{
				FileWriter fw = new FileWriter("1.txt");

				// write into a file, 2 in a row
				for(int i=0; i<outBuffer.length; i+=2)
				{
					int a = (outBuffer[i] & (outBuffer[i+1] << 8));// little endian
					fw.write(Integer.toString(a));
					fw.write('\n');
				}

				fw.flush();
				fw.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			return;
		}
		
		System.out.println("Which device to output? Enter a number");
		number = input.nextInt();
		Mixer mixerOut = AudioSystem.getMixer(mixerInfos[number]);
		Line.Info[] lineInfoOut = mixerOut.getSourceLineInfo();

		SourceDataLine lineOut;
		try
		{
			lineOut = (SourceDataLine) mixerOut.getLine(lineInfoOut[0]);
			lineOut.open(af);

			lineOut.start();
			byte[] outBuffer = record.toByteArray();
			System.out.println("Buffer length: " + outBuffer.length + " bytes");
			lineOut.write(outBuffer, 0, outBuffer.length);
			lineOut.close();
			lineOut.stop();
		}
		catch (Exception ex)
		{
			System.out.println(ex);
		}
		
	}
}

class Task2
{
	public Task2()
	{
		int t=0;

		AudioFormat af = new AudioFormat(44100f, 16, 1, true, false);
		System.out.println(af.toString());

		System.out.println("Which device for output? Enter a number");
		Scanner input = new Scanner(System.in);
		int number = input.nextInt();

		// get the corresponding mixer.
		Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
		Mixer mixer = AudioSystem.getMixer(mixerInfos[number]);
		Line.Info[] lineInfosOut = mixer.getSourceLineInfo();

		SourceDataLine lineOut;
		try
		{
			lineOut = (SourceDataLine) mixer.getLine(lineInfosOut[0]);
			lineOut.open(af);
			lineOut.start();
			byte[] outBuffer = new byte[44100 * 2];

			while(true)
			{
				double a;
				for(int i=0; i<44100; i++)
				{
					a = Math.sin(2f*Math.PI*1000*(t*44100+i)/44100) + Math.sin(2f*Math.PI*10000*(t*44100+i)/44100);
					// [a] may vary from -2 to 2.
					// has to use 16-bit sound to ensure quality.... Sad
					short ans = (short) Math.round(a * 32767 / 2);
					// for little endian, the lower byte stores low 8 bits
					// and the higher byte stores high 8 bits
					outBuffer[i * 2] = (byte) (ans & 0xFF);
					outBuffer[i * 2 + 1] = (byte) (ans >> 8);
				}
				lineOut.write(outBuffer, 0, outBuffer.length);
				t++;
			}

		}
		catch (Exception ex)
		{
			System.out.println(ex);
		}
	}
}

class Receiver implements Runnable
{
	public boolean stopped = false;
	public ByteArrayOutputStream out;
	public TargetDataLine line;
	public int recvCount = 0;

	public Receiver(TargetDataLine lin)
	{
		out = new ByteArrayOutputStream();
		this.line = lin;
	}

	public void run()
	{
		byte buf[] = new byte[512];
		try
		{
			line.start();
			while (!this.stopped)
			{
				recvCount += line.read(buf, 0, buf.length);
				out.write(buf);
			}
			line.stop();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}

class Task3
{
	private byte[] zero;
	private byte[] one;
	private double[] oneDouble;
	private byte[] headerBytes;
	private double[] headerDouble;
	private byte[] fileContent;

	private final int contentFreq = 4410;
	private final int lowFreq = 1000;
	private final int highFreq = 10000;
	private final int contentLen = 50; // the length of each number, ~0.001s
	private final int headerLen = 441; // 0.1s * 2

    private final int packSize = 2000;
    private final int recvPackages = 5;
    private int thresholdHeader = 30;
    private final int threshold10 = 0;

    private final boolean debug = false;
    private final boolean dumpHeader = true;

	private double[] b2d(byte[] input) // bytes to double
	{
		double[] ans = new double[input.length / 2];
		int tmp;
		for(int i=0; i*2<input.length; i++)
		{
			ans[i] = (short)(input[i*2] & (input[i*2+1] << 8)) / 32767f;
		}
		return ans;
	}

	private byte[] d2b(double[] input)
    {
        // input have to range between -1 to 1 !!!!!!!!
        int tmp;
        byte[] ans = new byte[input.length * 2];
        for(int i=0; i<input.length; i++)
        {
            tmp = (int) Math.round(input[i] * 32767f);
            ans[i*2] = (byte)(tmp & 0xFF);
            ans[i*2+1] = (byte)(tmp >> 8);
        }
        return ans;
    }

    private double[] corr(double[] signal, double[] ref)
    {
        double[] ans = new double[signal.length - ref.length];
        for(int i=0; i<ans.length; i++)
        {
            double sum = 0;
            for(int j=0; j<ref.length; j++)
                sum += (signal[i+j] * ref[j]);
            ans[i] = sum;
        }
        return ans;
    }

    private double[] genHeader()
    {
        int sampleCount = headerLen;
        double samFrequency = 44100;
        double t = (sampleCount - 1) / samFrequency;
        double frequencyMin = lowFreq;
        double frequencyMax = highFreq;
        double a = (frequencyMax - frequencyMin)/t;
        double[] header = new double[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++)
        {
            double phase = (i / samFrequency) * (i / samFrequency) * a * Math.PI + (i / samFrequency) * frequencyMin * 2 * Math.PI;
            header[i] = Math.cos(phase);
        }
        for (int i = sampleCount; i < sampleCount * 2; i++)
        {
            double phase = -((i - sampleCount) / samFrequency) * ((i - sampleCount) / samFrequency) * a * Math.PI + ((i - sampleCount) / samFrequency) * frequencyMax * 2 * Math.PI;
            header[i] = Math.cos(phase);
        }

        return header;
    }

	private double[] dotP(double[] sig, double[] ref)
	{
		double[] ans = new double[packSize];
		double sum = 0;
		for(int i=0; i<sig.length; i+=contentLen)
		{
			sum = 0;
			// ans[i] = sig[i] * ref[i % ref.length];
			for(int j=0; j<contentLen; j++)
				sum += sig[i+j] * ref[j];
			ans[i/contentLen] = sum;
		}
		return ans;
	}

	public Task3(boolean isSender)
	{
		// common use
		AudioFormat af = new AudioFormat(44100f, 16, 1, true, false);
		Scanner input = new Scanner(System.in);
		// gen header
        headerDouble = genHeader();
        headerBytes = d2b(headerDouble);

        if(debug)
        	thresholdHeader = 500;

		// generate 0, 1
        zero = new byte[contentLen * 2];
        one = new byte[contentLen * 2];
        oneDouble = new double[contentLen];
        double tmpDouble;
        short tmpShort;
        for(int i=0; i<contentLen; i++)
        {
            tmpDouble = Math.cos(2f * Math.PI * contentFreq * i / 44100 + Math.PI);
            tmpShort = (short) Math.round(tmpDouble * 32767);
            zero[i*2] = (byte)(tmpShort & 0xFF);
            zero[i*2+1] = (byte)(tmpShort >> 8);

            tmpDouble = Math.cos(2f * Math.PI * contentFreq * i / 44100);
            oneDouble[i] = tmpDouble;
            tmpShort = (short) Math.round(tmpDouble * 32767);
            one[i*2] = (byte)(tmpShort & 0xFF);
            one[i*2+1] = (byte)(tmpShort >> 8);
        }
        // do different things according to role
		if(isSender)
        {
			System.out.println("File name: input.txt");
			String fileName = "input.txt";
			//String fileName = input.nextLine();
			// open the file and read its contents
			try
			{
				FileInputStream is = new FileInputStream(fileName);
				int avail = is.available();
				fileContent = new byte[avail];
				is.read(fileContent);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			// Generate buffer
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try
			{
				for(int i=0; i< fileContent.length; i++)
				{
					if(i % packSize == 0)
						out.write(headerBytes);
					if(fileContent[i] == 48) // ASCII for '0'
						out.write(zero);
					else if(fileContent[i] == 49)
						out.write(one);
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			// Dumper
			if(debug)
			{
				// This code and the WAV util is adapted from here:
				// https://blog.csdn.net/qq_25925973/article/details/90441386
				int PCMSize = out.size();
				WaveHeader header = new WaveHeader();
				//长度字段 = 内容的大小（PCMSize) + 头部字段的大小(不包括前面4字节的标识符RIFF以及fileLength本身的4字节)
				header.fileLength = PCMSize + (44 - 8);
				header.FmtHdrLeth = 16;
				header.BitsPerSample = 16;
				header.Channels = 1;
				header.FormatTag = 0x0001;
				header.SamplesPerSec = 44100;
				header.BlockAlign = (short) (header.Channels * header.BitsPerSample / 8);
				header.AvgBytesPerSec = header.BlockAlign * header.SamplesPerSec;
				header.DataHdrLeth = PCMSize;

				byte[] h = new byte[0];
				try {
					h = header.getHeader();
				} catch (IOException e) {
					e.printStackTrace();
				}

				assert h.length == 44; //WAV标准，头部应该是44字节
				try
				{
					FileOutputStream fw = new FileOutputStream("1.wav");
					fw.write(h);
					out.writeTo(fw);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}

			// play sound
			System.out.println("Which device for output? Enter a number");
			int number = input.nextInt();
			Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
			Mixer mixer = AudioSystem.getMixer(mixerInfos[number]);
			Line.Info[] lineInfosOut = mixer.getSourceLineInfo();
			SourceDataLine lineOut;
			try
			{
				lineOut = (SourceDataLine) mixer.getLine(lineInfosOut[0]);
				lineOut.open(af);
				lineOut.start();
				byte[] buf = out.toByteArray();

				System.out.println("Waiting for preheat microphone....");
				//Thread.sleep(200); // wait for preheating
				System.out.println("Running.......");

				lineOut.write(buf, 0, buf.length);
				lineOut.drain();
				lineOut.close();
				System.out.println("Send Finish!");
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			// open device
			System.out.println("Which device to input? Enter a number");
			int number = input.nextInt();
			Mixer mixerOut = AudioSystem.getMixer(AudioSystem.getMixerInfo()[number]);
			Line.Info[] lineInfo = mixerOut.getTargetLineInfo();

			TargetDataLine line;
			try
			{
				line = (TargetDataLine) mixerOut.getLine(lineInfo[0]);
				line.open(af);

				FileWriter fw2 = new FileWriter("output.txt");

				Receiver recv = new Receiver(line);
				Thread timer = new Thread(recv);
				double[] timePtsBegin = new double[]{0, 2.1, 4.3, 6.7, 9, 11.5};

				System.out.println("Preheating Microphone...");
				timer.start();
				Thread.sleep(1000);
				recv.out.reset(); // clear the first 1s' noise

				for(int packageCnt = 0; packageCnt < recvPackages; packageCnt++)
				{
					Thread.sleep(800);
					System.out.printf("begin to fetch %dth header...\n", packageCnt);
					// find header and do correlation
					int head = (int) Math.round(timePtsBegin[packageCnt] * 44100);
					assert head % 2 == 0;

					double[] origData = b2d(recv.out.toByteArray());
					double[] headerData = new double[44100 / 2];
					System.arraycopy(origData, head, headerData, 0, headerData.length);
					double[] findHeader = corr(headerData, headerDouble);
					int headerPos = -1;
					double peak = -1;
					for(int p=0; p<findHeader.length; p++)
					{
						if(findHeader[p] > thresholdHeader)
						{
							for(int k=0; k<35; k++)
							{
								if(findHeader[p+k] > peak)
								{
									headerPos = p+k;
									peak = findHeader[p+k];
								}
							}
							System.out.printf("Located header at %d !!\n", headerPos);
							headerPos += headerLen * 2;
							break;
						}
					}
					if(headerPos == -1)
					{
						System.out.printf("FATAL: header%d not found...\n", packageCnt);
					}
					if(dumpHeader)
					{
						// dump header
						FileWriter fw = new FileWriter(String.format("header%d.txt", packageCnt));
						for(double i:findHeader)
						{
							fw.write(Double.toString(i));
							fw.write("\n");
						}
						fw.flush();
						fw.close();
					}
					if(headerPos == -1)
						throw new Exception("Header not found, stopped.");

					double[] nums = new double[contentLen * packSize];

					// wait until transmission finish
					while (recv.recvCount < 44100 * (timePtsBegin[packageCnt + 1] + 1.5) * 2)
						Thread.sleep(50);
					//recv.stopped = true;

					double[] data = b2d(recv.out.toByteArray());
					System.arraycopy(data, head + headerPos, nums, 0, nums.length);
					double[] zeroOnes = dotP(nums, oneDouble);

					if(packageCnt == 4)
						System.out.println("Finish. Writing to file.......");

					for(double avg:zeroOnes)
					{
						if(avg > threshold10)
							fw2.write("1");
						else
							fw2.write("0");
					}
				}
				recv.stopped = true;

				fw2.flush();
				fw2.close();

			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
}

public class Proj1
{
	public static void main(String[] args) {
		System.out.println("=========================");
		System.out.println("   Which to execute?");
		System.out.println("-------------------------");
		System.out.println("   1. Part1 -> CK1");
		System.out.println("   2. Part1 -> CK2");
		System.out.println("   3. Part2");
		System.out.println("   4. Part3 -> Receive");
		System.out.println("   5. Part3 -> Send");
		System.out.println("   6. Dump record");
		System.out.println("   7. Listen to music");
		System.out.println("=========================");

		Scanner input = new Scanner(System.in);
		int sel = input.nextInt();
		switch(sel)
		{
			case 1:
				Task1 t1 = new Task1(false, false);
				break;
			case 2:
				Task1 tsk = new Task1(true, false);
				break;
			case 3:
				Task2 t2 = new Task2();
				break;
			case 4:
				Task3 t31 = new Task3(false);
				break;
			case 5:
				Task3 t32 = new Task3(true);
				break;
			case 6:
				Task1 tsk1 = new Task1(false, true);
				break;
			case 7:
				MusicPlayer mp = new MusicPlayer(input);
				mp.run();
				break;
			default:
				System.out.println("Not implemented yet!");
		}
	}
}

class WaveHeader {

	public final char fileID[] = {'R', 'I', 'F', 'F'};
	public int fileLength;
	public char wavTag[] = {'W', 'A', 'V', 'E'};;
	public char FmtHdrID[] = {'f', 'm', 't', ' '};
	public int FmtHdrLeth;
	public short FormatTag;
	public short Channels;
	public int SamplesPerSec;
	public int AvgBytesPerSec;
	public short BlockAlign;
	public short BitsPerSample;
	public char DataHdrID[] = {'d','a','t','a'};
	public int DataHdrLeth;

	public byte[] getHeader() throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		WriteChar(bos, fileID);
		WriteInt(bos, fileLength);
		WriteChar(bos, wavTag);
		WriteChar(bos, FmtHdrID);
		WriteInt(bos,FmtHdrLeth);
		WriteShort(bos,FormatTag);
		WriteShort(bos,Channels);
		WriteInt(bos,SamplesPerSec);
		WriteInt(bos,AvgBytesPerSec);
		WriteShort(bos,BlockAlign);
		WriteShort(bos,BitsPerSample);
		WriteChar(bos,DataHdrID);
		WriteInt(bos,DataHdrLeth);
		bos.flush();
		byte[] r = bos.toByteArray();
		bos.close();
		return r;
	}

	private void WriteShort(ByteArrayOutputStream bos, int s) throws IOException {
		byte[] mybyte = new byte[2];
		mybyte[1] =(byte)( (s << 16) >> 24 );
		mybyte[0] =(byte)( (s << 24) >> 24 );
		bos.write(mybyte);
	}


	private void WriteInt(ByteArrayOutputStream bos, int n) throws IOException {
		byte[] buf = new byte[4];
		buf[3] =(byte)( n >> 24 );
		buf[2] =(byte)( (n << 8) >> 24 );
		buf[1] =(byte)( (n << 16) >> 24 );
		buf[0] =(byte)( (n << 24) >> 24 );
		bos.write(buf);
	}

	private void WriteChar(ByteArrayOutputStream bos, char[] id) {
		for (int i=0; i<id.length; i++) {
			char c = id[i];
			bos.write(c);
		}
	}
}