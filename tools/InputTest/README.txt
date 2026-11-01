Desktop test of HardwareInput (keyboard/trackball/mouse <-> touch switching,
trackball calibration). Uses tiny fake android.view/os/content classes.
Run from this folder:
  S=../../src/com/mojang/rubydung
  javac -cp <android.jar> -d out stub/android/*/*.java \
    $S/{Game,Player,HitResult,Raycaster,InputState,Timer,HardwareInput,MotionEventCompat,TouchControls}.java \
    $S/level/*.java $S/phys/*.java T.java
  java -cp out:<android.jar> T
(android.jar is only needed for the javax.microedition GL10 types.)
