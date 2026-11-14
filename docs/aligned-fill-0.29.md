# Align the fill with the glass capsule, 0.29

The user's 0.28 screenshot shows the original narrow white fill inside the wide
glass. Restoring the native square View fixed low-level clipping, but exposed
another layout lifecycle issue: the View was already square before styling.
Changing minHeight/maxHeight and requesting layout does not change its measured
591 x 591 size, so AbsSeekBar.onSizeChanged does not refresh the drawable bounds.
The previous 118px track bounds survive inside the new 191px container.

After the existing one-shot layout callback, explicitly refresh the original
progress drawable's bounds to the centered capsule thickness. Samsung's own
onBoundsChange/onLevelChange propagate this to all nested progress layers. Keep
the square canvas and native drawable instances. No draw or touch hooks, no
audio or color changes, no intrinsic-size guesses and no frame loop. Save the
original drawable bounds for rollback and ignore a queued update after restore.

The regression test now begins with the real nested drawable structure and a
fully laid-out 118px track, then applies the width change to the same square
SeekBar. It reproduces the stale bounds before the fix, verifies 191px bounds
afterward and checks pixels near both capsule edges at several volume levels.
The previous first-step/low-level and Design Studio tests remain enabled.

The approved glass, icon, dimensions and Design Studio artwork are unchanged.
On-phone confirmation is still required after automated checks and compilation.
