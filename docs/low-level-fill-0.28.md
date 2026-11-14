# Visible low-volume fill and refreshed Design Studio, 0.28

## Root cause found in the supplied Samsung APK

SecRoundedCornerSeekBarDrawable.draw switches branches at
`bounds.height * 10000 / bounds.width`. With the approved 591 x 191 horizontal
track (rotated vertically), this threshold is 3231/10000, about 32.3%.
The low branch passes `centerY + radius` as RectF.top and `centerY - radius`
as RectF.bottom. This inverted rectangle draws no fill. Above the threshold,
Samsung uses the nested GradientDrawable, so a whole circular cap suddenly
appears. Five standard 1/15 key increments exceed that threshold. This matches
the user's report of sound changing without visible white until the fifth press.

0.27 addressed touch mapping rather than this rendering defect. Its precision
hook and helper are removed, restoring the original Samsung touch calculation.

## Narrow rendering repair

Register only progress drawable instances belonging to styled compact rows.
Intercept the low branch of their draw method, use ordered bounds, preserve
native start/end/button colors, and restore Canvas clipping in a finally block.
Use the actual bounds offsets and clamp the low cap radius to nonnegative values.
At zero there is no fill. Above the threshold the original Samsung draw method
runs unchanged. Unregistered instances (including expanded panels) remain native.

The original progress drawable is never replaced; level changes, casts, audio,
warnings, volume keys, touch listeners and animations remain Samsung's. Rendering
does not mutate views/drawables, request layout, invalidate, or start a frame loop.
Paint, Path and rectangles are reused; geometry/shader updates are cached. On
failure unregister the affected renderer and fall back to native drawing.
Rollback/detachment unregisters it. Glass, outline, dimensions and native speaker
artwork remain unchanged from 0.26.

## Studio, separately requested by the user

The large preview uses the current speaker drawable and shared low-fill renderer,
with a proportional fill and the same 0.62 fill opacity. Add simulated single-step
controls, 0/1/5/33/100 percent presets, a level readout, and preserve level on
recreation. Preview drags prevent the surrounding ScrollView from intercepting.
The studio controls do not change device audio. The preview describes its blur as
an illustration; it does not claim to validate SystemUI composition.

## Verification

Robolectric native Android graphics tests reproduce the inverted-rectangle blank
output and inspect actual bitmap pixels at the first native media step and across
the formerly blank low range. They also cover mute, native upper-range fallback,
cap continuity, Canvas state restoration, offset bounds, descending levels and
studio creation/rendering. Device validation is still needed for the Samsung
hook and the physical +/- buttons. Tests are not a claim of phone validation.
