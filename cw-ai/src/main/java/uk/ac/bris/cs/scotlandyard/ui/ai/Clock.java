package uk.ac.bris.cs.scotlandyard.ui.ai;

public class Clock {
    //Stores the time in nanoseconds in the program's execution that the clock was created
    private long initialTime;
    //Stores how long in nanoseconds it has been since the clock's creation - updated when updateClock is called
    private long time;
    //How long in seconds before checkLimit starts returning true, causing the AI to return before completing
    private double timeLimit;

    /**
     * @param timeLimit the length of time the AI is allowed to run pickMove for
     *                  checkLimit returns true once time has exceeded this amount
     */
    public Clock(double timeLimit){
        this.timeLimit = timeLimit;
        initialTime = System.nanoTime();
    }

    /**
     * updates time with the new length of time AI has been 'thinking' for
     */
    public void updateClock(){
        time = System.nanoTime();
        time -= initialTime;
    }

    /**
     * @return true if time (in seconds) is greater than timeLimit
     *         otherwise: false
     */
    public boolean checkLimit(){
        return Long.valueOf(time).doubleValue() / 1000000000 >= timeLimit;
    }
}
