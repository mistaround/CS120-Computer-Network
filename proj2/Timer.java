class Timer implements Runnable
{
    public boolean isTimeUp;
    protected int time;
    public boolean ok;

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
        System.out.println("Time up!!");
        if(!ok)
            System.out.println(" --- Link Error!!!! ---");
        this.isTimeUp = true;
    }
}