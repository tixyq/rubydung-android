# RubyDung for Android

**The prototype that became Minecraft, now in your pocket. Build 132211, May 2009.**

RubyDung is the tiny Java experiment Markus "Notch" Persson wrote before Minecraft existed: a lump of blocks, a first-person camera, and two verbs, *place* and *break*. This is a faithful Android port of its earliest surviving build, `rd-132211`, that runs on anything from **Android 1.0 to the latest release**.

No menus, no loading screens, no accounts, no ads, no internet. You open it and you are standing in a world.

[Русская версия](README.ru.md)

---

## Features

- **Faithful to the original.** Same player hitbox, gravity, jump impulse and friction, same terrain texture, same place-a-block, break-a-block gameplay.
- **Boots straight into the world.** Like the 2009 build, there is no menu and no pause screen. Your world is saved automatically when you leave.
- **Touch controls that feel right.** A 3x3 D-pad on the left, drag anywhere to look, tap to place, hold to break.
- **Plays well with real hardware.** Plug in a keyboard, mouse or gamepad and the touch interface disappears completely. Touch the screen and it comes straight back.
- **Built for old phones too.** Slide-out keyboards and **trackballs** are supported: arrows and trackball look around like a mouse, Enter places, Del breaks.
- **Runs everywhere.** Targets API 1 (Android 1.0) and still installs on current Android. Hand-written EGL 1.0 and OpenGL ES 1.0 rendering, no GLSurfaceView, no support libraries.
- **Tiny and private.** No permissions, no network access, no tracking.

## Controls

### Touch (default)

| Action | Gesture |
|---|---|
| Move | 3x3 D-pad, lower left (corners are diagonals) |
| Jump | Centre of the D-pad |
| Look | Drag anywhere else on the screen |
| Place a block | Short tap |
| Break a block | Hold still for 0.4 s, keep holding to keep breaking |

### Keyboard, trackball and mouse

As soon as one of these is used, the on-screen controls hide and stop working. **One touch on the screen brings them all back.**

| Action | Keyboard | Trackball | Mouse |
|---|---|---|---|
| Move | `W` `A` `S` `D` | | |
| Jump | `Space` | | |
| Look | Arrow keys | Roll the ball | Move the mouse |
| Place a block | `Enter` | Short press | Left click |
| Break a block | `Del` | | Right click |
| Recalibrate trackball | `C` | Long press (about 1 s) | |
| Quit | `Back` / `Esc` | | |

Keyboard and trackball aim at the centre of the screen; the mouse aims at its cursor (or the centre when the pointer is captured, Android 8+).

### Gamepad

Left stick moves, right stick looks, `A` jumps, `X` places, `B` breaks.

### Trackball calibration

Phones disagree about which way "right" is on a trackball, depending on where it sits and how the system rotates it for landscape. The first time you roll the ball, the game dims the screen and shows an arrow pointing right: roll the ball that way once. The answer is remembered per screen rotation. Any other key skips it, `C` or a long press on the ball starts it again. Phones without a trackball never see this.

## Install

1. Download `RubyDung-rd-132211.apk` from the Releases page.
2. Allow installing from unknown sources if Android asks.
3. Open **RubyDung** and play.

Requires any Android device with OpenGL ES 1.0 (that is all of them).

## Build from source

The project builds without Gradle or Android Studio, from a single script.

```sh
./build.sh
```

It was written for [Termux](https://termux.dev) on Android but works on any Linux with the tools below. Set `SDK` to a folder containing `platforms/android-28/android.jar` (or `android-35`).

Needs: `javac` (JDK 8+), `d8`, `aapt`, `zipalign`, `apksigner`, `keytool`.

Output: `out/RubyDung-rd-132211.apk`, signed with the bundled **debug** keystore (v1, v2 and v3 signatures, `minSdkVersion` 1, `targetSdkVersion` 24). Use your own keystore for anything you publish.

## Project layout

```
src/com/mojang/rubydung/
  RubyDungActivity.java   entry point
  GameView.java           SurfaceView + EGL 1.0 / GLES 1.0 loop
  Game.java, Player.java  world, physics, place/break
  Raycaster.java          block picking
  Renderer.java           world, highlight, touch UI, calibration hint
  TouchControls.java      MCPE-style touch interface
  HardwareInput.java      keyboard, trackball, mouse, gamepad
  MotionEventCompat.java  reflection shims for APIs newer than Android 1.0
  level/                  blocks, chunk meshing, world renderer
assets/                   terrain.png, controls.png
tools/                    desktop tests
```

### Tests

The game logic and the input handling run on a plain desktop JVM.

```sh
# physics, raycasting, world, meshing
javac -d build/testclasses <pure sources> tools/LogicTest.java
java -cp build/testclasses LogicTest

# keyboard / trackball / mouse / touch switching, trackball calibration
# see tools/InputTest/README.txt
```

## Credits and legal

RubyDung was created by Markus Persson. `rd-132211` is the original build of May 13, 2009. The world texture is the original `terrain.png`; the touch-control artwork is styled after Minecraft Pocket Edition 0.6.1.

This is an unofficial fan port and a piece of gaming history preserved. It is **not affiliated with, endorsed by, or connected to Mojang Studios, Microsoft or Notch Development AB.** Minecraft is a trademark of Mojang Synergies AB. If you are a rights holder and want something changed or removed, open an issue.

License: MIT, see `LICENSE`. Original RubyDung is by Markus Persson.
