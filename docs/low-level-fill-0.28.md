# Visible low-volume fill and refreshed Design Studio, 0.28

## Confirmed geometry mismatch

The supplied SystemUI APK's volume_row_view.xml gives VolumeSeekBar width equal
to the capsule height, height MATCH_PARENT, CENTER gravity and rotation 270.
It is intentionally a square view. AbsSeekBar centers the narrower progress
track inside that square. SecRoundedCornerSeekBarDrawable.draw assumes this
when centering its low-level clip at Y = bounds.width / 2.

Our earlier layout resized the view to 591 x 191 instead of 591 x 591.
The low-level circular clip still occupied Y=200..391, outside the actual
0..191 viewport. Above 191/591 (~32.3%), Samsung switches to its nested drawable,
which uses the real bounds. Five usual 1/15 key increments cross that threshold.
This matches sound changing without white fill until about the fifth press.

The inverted rectangle in Samsung's draw code is not the root cause: Android
normalizes it. The initial graphics test established that, so the final
regression includes native clip coordinates, View rotation and actual viewport.

## Fix: preserve the original square view

Change only `resize(seek, height, width)` to `resize(seek, height, height)`.
Keep maxHeight/minHeight at the approved capsule width. CENTER gravity and the
270-degree rotation place the same 191 x 591 visible track in exactly the same
location. Restore the native 0.26 touch path by removing the speculative 0.27
precision hook and helper. No draw hook, progress drawable replacement, audio
changes, listener changes, global volume-step settings, or frame loop.

Glass, rim, blur, outer dimensions and speaker drawing stay unchanged. Samsung
owns fill drawing, level updates, keys, touch, animations and accessibility.

## Requested Studio update

Use the current speaker drawable and illustrate the native low cap using a
bounds-local renderer in the Studio only. Add 0/1/5/33/100 percent presets,
single-step controls, level readout, and preserve level on recreation. Preview
drags prevent ScrollView interception. Reset the backdrop brush each frame so
label alpha does not leak into the next frame. Studio controls simulate volume
without changing device audio or claiming real SystemUI blur.

## Verification

Robolectric native Android graphics tests reproduce the blank low range with
the original Samsung draw equations on the old rectangular view, then verify
visible pixels at the first native media step on the restored square view.
An actual SeekBar layout test checks its bounds and position after rotation
setup. Further tests cover Studio low levels, mute, cap continuity, clipping,
descending values, and activity/preview rendering. Device validation of the
actual Samsung hooks and physical +/- buttons remains necessary.
