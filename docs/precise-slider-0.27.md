# Compact media slider precision, 0.27

Only drag-to-volume mapping changes. All 0.26 geometry, blur, shell, rim,
speaker graphics, opacity, progress drawables and preview files are unchanged.

## Evidence and limits

The supplied SystemUI APK's `VolumeSeekBar.onTouchEvent` records `touchedX`
and `currentProgress` on ACTION_DOWN. `trackTouchEvent(delta)` computes
`round(currentProgress + delta / usableWidth * (max - min))`. This is relative
dragging: reaching the bottom of the capsule need not reach minimum volume if
the gesture began below the fill boundary. It is not a fixed one-third minimum.
`VolumePanelReducer.getImpliedLevel` rounds media progress down to multiples of
10. The screenshot alone cannot establish the actual audio level or prove
that touch mapping is its only cause.

## Targeted change

Hook only `trackTouchEvent(float)` for stream 3, rotated 270 degrees, belonging
to a currently styled compact row. Anchor each gesture at its original level,
map its remaining physical travel to the native endpoints, and use a squared
curve for finer adjustment near zero. Convert that target back to Samsung's
delta argument and invoke the original method exactly once. Its tracking,
listeners, audio dispatch, bounds, step quantization, warnings and haptics remain
native. No direct AudioManager calls, new touch listener, replacement drawable,
draw hook, or global volume-step setting. No changes to expanded panels.

Invalid geometry or reflection failures fall back to the original argument.
Detached or restored rows automatically lose the precision mapping. Hook setup
failure leaves native touch behavior intact.

## Validation

JUnit tests execute the mapping followed by Samsung's rounding equation and
check no jump at touch/reversal, reachable endpoints, clamping, monotonicity,
nonzero minimum, low-end resolution including the first positive media step,
and unsupported-geometry fallback. CI runs tests before assembling the APK.
The audio and touch feel still require validation on the phone. This does not
claim new audio levels below Samsung's smallest native media step.
