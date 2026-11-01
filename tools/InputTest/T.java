import com.mojang.rubydung.*;
import android.view.*;
import java.io.File;
public class T {
  static class Prefs implements android.content.SharedPreferences {
    java.util.Map<String,Integer> m=new java.util.HashMap<String,Integer>();
    public int getInt(String k,int d){ Integer v=m.get(k); return v==null?d:v; }
    public Editor edit(){ final Prefs p=this; return new Editor(){ public Editor putInt(String k,int v){p.m.put(k,v);return this;} public boolean commit(){return true;} }; }
  }
  static MotionEvent ball(float x,float y){ return new MotionEvent(MotionEvent.ACTION_MOVE,x,y); }
  static MotionEvent ball(int act,long t){ MotionEvent e=new MotionEvent(act,0,0); e.time=t; return e; }
  static float yawAfter(HardwareInput hw,Game g,TouchControls tc,float x,float y){ float y0=g.player.yRot,p0=g.player.xRot; g.player.xRot=0; hw.onTrackballEvent(ball(x,y)); frame(hw,g,tc); float r=g.player.yRot-y0; lastPitch=g.player.xRot; return r; }
  static float lastPitch;
  static int fail=0;
  static void ck(boolean ok,String w){ System.out.println((ok?"  ok  ":"FAIL  ")+w); if(!ok) fail++; }
  static final int W=800,H=480;
  static void frame(HardwareInput hw,Game g,TouchControls tc){ hw.update(g,tc,W,H,1.0F); }
  public static void main(String[] a) throws Exception {
    File d=new File("world"); d.mkdirs(); new File(d,"level.dat").delete();
    Game g=new Game(d); TouchControls tc=new TouchControls(g); tc.setSize(W,H);
    Prefs prefs=new Prefs(); HardwareInput hw=new HardwareInput(g); hw.setPreferences(prefs);
    for(int i=0;i<200;i++) g.tick();
    g.player.xRot=45; g.player.yRot=0; // look down at the ground
    frame(hw,g,tc); ck(tc.visible,"start: touch UI visible");

    System.out.println("keyboard hides UI");
    hw.onKeyDown(KeyEvent.KEYCODE_MENU,new KeyEvent(0)); frame(hw,g,tc); ck(tc.visible,"Menu key does not hide UI");
    hw.onKeyDown(KeyEvent.KEYCODE_W,new KeyEvent(0)); frame(hw,g,tc);
    ck(!tc.visible,"W hides UI"); ck(g.input.forward,"W moves forward");
    hw.onKeyUp(KeyEvent.KEYCODE_W,new KeyEvent(0)); frame(hw,g,tc); ck(!g.input.forward,"W released");
    ck(!hw.touchControlsVisible(),"touchControlsVisible()=false");

    System.out.println("arrows look like a mouse (no movement)");
    float y0=g.player.yRot,x0=g.player.xRot;
    hw.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(0)); frame(hw,g,tc); Thread.sleep(50); frame(hw,g,tc); Thread.sleep(50); frame(hw,g,tc);
    ck(g.player.yRot>y0,"RIGHT turns right ("+(g.player.yRot-y0)+" deg)"); ck(!g.input.right&&!g.input.forward,"arrows do not walk");
    hw.onKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(0));
    y0=g.player.yRot; hw.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT,new KeyEvent(0)); frame(hw,g,tc);Thread.sleep(50);frame(hw,g,tc); ck(g.player.yRot<y0,"LEFT turns left"); hw.onKeyUp(KeyEvent.KEYCODE_DPAD_LEFT,new KeyEvent(0));
    x0=g.player.xRot; hw.onKeyDown(KeyEvent.KEYCODE_DPAD_UP,new KeyEvent(0)); frame(hw,g,tc);Thread.sleep(50);frame(hw,g,tc); ck(g.player.xRot<x0,"UP looks up"); hw.onKeyUp(KeyEvent.KEYCODE_DPAD_UP,new KeyEvent(0));
    x0=g.player.xRot; hw.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN,new KeyEvent(0)); frame(hw,g,tc);Thread.sleep(50);frame(hw,g,tc); ck(g.player.xRot>x0,"DOWN looks down"); hw.onKeyUp(KeyEvent.KEYCODE_DPAD_DOWN,new KeyEvent(0));
    y0=g.player.yRot; frame(hw,g,tc); Thread.sleep(30); frame(hw,g,tc); ck(g.player.yRot==y0,"released arrows stop turning");

    System.out.println("trackball: first roll starts calibration");
    y0=g.player.yRot; x0=g.player.xRot;
    ck(hw.onTrackballEvent(ball(1f,0f)),"trackball consumed"); frame(hw,g,tc);
    ck(hw.isCalibratingTrackball(),"first roll starts calibration"); ck(g.player.yRot==y0,"view does not move while calibrating");
    ck(hw.getCalibrationProgress()>0f&&hw.getCalibrationProgress()<1f,"progress shown ("+hw.getCalibrationProgress()+")");
    hw.onTrackballEvent(ball(2.5f,0.3f)); frame(hw,g,tc);
    ck(!hw.isCalibratingTrackball(),"calibration done after rolling right"); ck(prefs.m.get("trackball_rot_0")==0,"saved rot 0");
    ck(hw.getCalibrationProgress()<0f,"no overlay afterwards");
    y0=g.player.yRot; x0=g.player.xRot; hw.onTrackballEvent(ball(1f,0.5f)); frame(hw,g,tc);
    ck(Math.abs(g.player.yRot-y0-3.0f)<0.01f,"ball +1 x = +3 deg yaw ("+(g.player.yRot-y0)+")"); ck(Math.abs(g.player.xRot-x0-1.5f)<0.01f,"ball +0.5 y = +1.5 deg pitch");
    MotionEvent m=new MotionEvent(MotionEvent.ACTION_MOVE,1f,0f); m.hx=new float[]{1f,1f}; m.hy=new float[]{0,0}; y0=g.player.yRot; hw.onTrackballEvent(m); frame(hw,g,tc);
    ck(Math.abs(g.player.yRot-y0-9.0f)<0.01f,"batched history is summed");

    System.out.println("trackball: every mounting orientation");
    float[][] raw={{1,0},{0,1},{-1,0},{0,-1}}; // raw direction the user rolled for "screen right"
    String[] nm={"raw right","raw down","raw left","raw up"};
    for(int r=0;r<4;r++){
      Prefs p2=new Prefs(); HardwareInput h2=new HardwareInput(g); h2.setPreferences(p2);
      h2.onTrackballEvent(ball(raw[r][0]*4f,raw[r][1]*4f)); frame(h2,g,tc);
      ck(!h2.isCalibratingTrackball()&&p2.m.get("trackball_rot_0")==r,nm[r]+" -> rot "+r);
      float yaw=yawAfter(h2,g,tc,raw[r][0],raw[r][1]); ck(Math.abs(yaw-3f)<0.01f,"  "+nm[r]+" turns view right (+"+yaw+")");
      yaw=yawAfter(h2,g,tc,-raw[r][0],-raw[r][1]); ck(Math.abs(yaw+3f)<0.01f,"  opposite turns left ("+yaw+")");
      // screen-down = raw right rotated the same way
      float dx=-raw[r][1], dy=raw[r][0]; // rotate the "right" vector 90deg clockwise on screen => down
      yawAfter(h2,g,tc,dx,dy); ck(Math.abs(lastPitch-3f)<0.01f,"  rolling toward screen-down looks down (+"+lastPitch+")");
    }

    System.out.println("trackball: calibration details");
    { Prefs p2=new Prefs(); HardwareInput h2=new HardwareInput(g); h2.setPreferences(p2);
      h2.onTrackballEvent(ball(2f,2f)); h2.onTrackballEvent(ball(1.5f,1.5f)); frame(h2,g,tc);
      ck(h2.isCalibratingTrackball()&&p2.m.isEmpty(),"diagonal roll is rejected, asks again");
      h2.onKeyDown(KeyEvent.KEYCODE_SPACE,new KeyEvent(0)); h2.onKeyUp(KeyEvent.KEYCODE_SPACE,new KeyEvent(0)); frame(h2,g,tc);
      ck(!h2.isCalibratingTrackball()&&p2.m.get("trackball_rot_0")==0,"any key cancels -> default saved");
      HardwareInput h3=new HardwareInput(g); h3.setPreferences(p2); h3.onTrackballEvent(ball(1f,0f)); frame(h3,g,tc);
      ck(!h3.isCalibratingTrackball(),"saved calibration survives a restart");
      // per display rotation
      h3.setDisplayRotation(3); h3.onTrackballEvent(ball(1f,0f)); frame(h3,g,tc);
      ck(h3.isCalibratingTrackball(),"other landscape asks separately");
      h3.onTrackballEvent(ball(0f,-4f)); frame(h3,g,tc); ck(p2.m.get("trackball_rot_3")==3,"stored under rotation 3");
      h3.setDisplayRotation(0); ck(!h3.isCalibratingTrackball(),"back to rotation 0 uses its own value");
      h3.setDisplayRotation(3); y0=g.player.yRot; h3.onTrackballEvent(ball(0f,-1f)); frame(h3,g,tc); ck(Math.abs(g.player.yRot-y0-3f)<0.01f,"rotation 3 mapping applied");
      // C key and long press recalibrate; short press places
      h3.onKeyDown(KeyEvent.KEYCODE_C,new KeyEvent(0)); h3.onKeyUp(KeyEvent.KEYCODE_C,new KeyEvent(0)); ck(h3.isCalibratingTrackball(),"C key recalibrates");
      h3.onTrackballEvent(ball(4f,0f)); ck(!h3.isCalibratingTrackball()&&p2.m.get("trackball_rot_3")==0,"C: new value saved");
      h3.onTrackballEvent(ball(MotionEvent.ACTION_DOWN,1000)); h3.onTrackballEvent(ball(MotionEvent.ACTION_UP,2500)); ck(h3.isCalibratingTrackball(),"long press recalibrates");
      h3.onTouchInput(); ck(!h3.isCalibratingTrackball(),"touch cancels calibration");
      h3.onTrackballEvent(ball(1f,0f)); ck(!h3.isCalibratingTrackball(),"after a cancelled recalibration the old value is kept");
    }
    System.out.println("mouse/touchpad posing as a trackball is ignored");
    { Prefs p4=new Prefs(); HardwareInput h4=new HardwareInput(g); h4.setPreferences(p4);
      int[] srcs={0x2002,0x20004,0x00100008|0x10004};
      for(int sc: srcs){ MotionEvent e=ball(3f,0f); e.source=sc; ck(!h4.onTrackballEvent(e),"source 0x"+Integer.toHexString(sc)+" not handled as trackball"); }
      frame(h4,g,tc); ck(!h4.isCalibratingTrackball()&&p4.m.isEmpty()&&tc.visible,"no calibration, UI untouched");
      MotionEvent real=ball(1f,0f); real.source=0x10004; ck(h4.onTrackballEvent(real)&&h4.isCalibratingTrackball(),"real trackball source (0x10004) still works"); }

    System.out.println("Enter places, Del breaks");
    g.player.xRot=60; g.player.yRot=0; frame(hw,g,tc); g.updateHitAt(W*0.5f,H*0.5f,W,H,1f);
    ck(g.hit!=null,"aiming at ground");
    int bx=g.hit.x,by=g.hit.y,bz=g.hit.z;
    hw.onKeyDown(KeyEvent.KEYCODE_DEL,new KeyEvent(0)); hw.onKeyUp(KeyEvent.KEYCODE_DEL,new KeyEvent(0)); frame(hw,g,tc);
    ck(!g.level.isSolidTile(bx,by,bz),"DEL broke the block");
    g.updateHitAt(W*0.5f,H*0.5f,W,H,1f); bx=g.hit.x;by=g.hit.y;bz=g.hit.z; int face=g.hit.face;
    int px=bx,py=by,pz=bz; switch(face){case 0:py--;break;case 1:py++;break;case 2:pz--;break;case 3:pz++;break;case 4:px--;break;default:px++;}
    ck(!g.level.isSolidTile(px,py,pz),"target cell empty before placing (face "+face+")");
    hw.onKeyDown(KeyEvent.KEYCODE_ENTER,new KeyEvent(0)); hw.onKeyUp(KeyEvent.KEYCODE_ENTER,new KeyEvent(0)); frame(hw,g,tc);
    ck(g.level.isSolidTile(px,py,pz),"ENTER placed a block exactly there");
    // holding / auto-repeat must not spam
    g.updateHitAt(W*0.5f,H*0.5f,W,H,1f);
    hw.onKeyDown(KeyEvent.KEYCODE_DEL,new KeyEvent(0)); hw.onKeyDown(KeyEvent.KEYCODE_DEL,new KeyEvent(1)); hw.onKeyDown(KeyEvent.KEYCODE_DEL,new KeyEvent(2));
    ck(hw.onKeyDown(KeyEvent.KEYCODE_DEL,new KeyEvent(3)),"DEL is a consumed game key");
    hw.onKeyUp(KeyEvent.KEYCODE_DEL,new KeyEvent(0));
    ck(hw.onKeyDown(KeyEvent.KEYCODE_ENTER,new KeyEvent(0)),"ENTER consumed"); hw.onKeyUp(KeyEvent.KEYCODE_ENTER,new KeyEvent(0));
    // trackball press places
    g.updateHitAt(W*0.5f,H*0.5f,W,H,1f); bx=g.hit.x;by=g.hit.y;bz=g.hit.z; ck(!g.level.isSolidTile(bx,by+1,bz),"cell empty before trackball click");
    hw.onTrackballEvent(ball(MotionEvent.ACTION_DOWN,5000)); frame(hw,g,tc); ck(!g.level.isSolidTile(bx,by+1,bz),"press alone does not place yet");
    hw.onTrackballEvent(ball(MotionEvent.ACTION_UP,5120)); frame(hw,g,tc);
    ck(g.level.isSolidTile(bx,by+1,bz),"short trackball click places a block");

    System.out.println("touch brings everything back");
    hw.onKeyDown(KeyEvent.KEYCODE_D,new KeyEvent(0)); // key still 'held'
    ck(hw.onTouchInput(),"touch switched back from keyboard"); frame(hw,g,tc);
    ck(tc.visible,"touch UI visible again"); ck(!g.input.right,"held key forgotten");
    ck(!hw.onTouchInput(),"second touch is a no-op");
    // trackball alone then touch
    hw.onTrackballEvent(ball(1f,0f)); frame(hw,g,tc); ck(!tc.visible,"trackball hides UI");
    hw.onTouchInput(); frame(hw,g,tc); ck(tc.visible,"touch restores after trackball");
    // touch controls actually work again: press forward on pad
    tc.handleTouch(TouchControls.ACTION_DOWN,0,tc.padX+tc.btn*1.5f,tc.padY+tc.btn*0.5f,1000); frame(hw,g,tc);
    ck(g.input.forward,"on-screen D-pad works again");
    tc.cancelAll();

    System.out.println("mouse then touch");
    MotionEvent me=new MotionEvent(MotionEvent.ACTION_MOVE,100,100);
    // emulate mouse detection via onGenericMotionEvent is source-based; use reflection on private detectMouse
    java.lang.reflect.Method dm=HardwareInput.class.getDeclaredMethod("detectMouse"); dm.setAccessible(true); dm.invoke(hw);
    frame(hw,g,tc); ck(hw.isMouseDetected()&&!tc.visible,"mouse hides UI");
    ck(hw.onTouchInput(),"finger switches back from mouse"); frame(hw,g,tc);
    ck(!hw.isMouseDetected()&&tc.visible,"mouse mode off, UI visible");
    System.out.println(fail==0?"ALL OK":"FAILURES: "+fail); System.exit(fail==0?0:1);
  }
}
