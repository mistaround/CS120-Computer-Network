public class binUtils
{
    public static int getNum(int beginPos, int length, byte[] buf)
    {
        int result = 0;
        for(int i=0; i < length; i++)
            result = (result << 1) + buf[beginPos + i];
        return result;
    }

    public static int getTimeStamp()
    {
        long a = System.currentTimeMillis();
        return (int) (a % 1000000000);
    }
}
