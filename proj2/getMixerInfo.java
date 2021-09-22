import javax.sound.sampled.*;

public class getMixerInfo
{
	public static void main(String[] args) 
	{
		Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
		int number = 0;
		for (Mixer.Info mixerInfo : mixerInfos)
		{
			System.out.println("#" +  number + " Device:");
			System.out.println(mixerInfo.toString());
			Mixer mixer = AudioSystem.getMixer(mixerInfo);

			Line.Info[] tLines = mixer.getTargetLineInfo();
			Line.Info[] sLines = mixer.getSourceLineInfo();
			System.out.println("Target lines (input):" + tLines.length);
			for(Line.Info inf : tLines)
			{
				System.out.println(inf.toString());
			}
			System.out.println("Source lines (output):" + sLines.length);
			for(Line.Info inf : sLines)
			{
				System.out.println(inf.toString());
			}
			System.out.println("===============");
			number ++;
		}
	}
}