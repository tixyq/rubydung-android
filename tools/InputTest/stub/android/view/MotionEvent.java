package android.view;
public class MotionEvent {
  public static final int ACTION_DOWN=0, ACTION_UP=1, ACTION_MOVE=2, ACTION_CANCEL=3;
  public int action; public float x,y; public float[] hx=new float[0], hy=new float[0];
  public MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}
  public long time; public int source; public int getSource(){return source;} public long getEventTime(){return time;} public int getAction(){return action;} public float getX(){return x;} public float getY(){return y;}
  public int getHistorySize(){return hx.length;} public float getHistoricalX(int i){return hx[i];} public float getHistoricalY(int i){return hy[i];}
}
