# 0.25: eliminate the narrow track inside the widened capsule

User tested 0.24: audio and gestures work, no reported stability issue. The
home-screen image 2303.jpg shows a narrow inner perimeter inside the wide glass.
The app screenshot also contains the enlarged preview behind the real panel;
that separate preview is not a second SystemUI window.

Framework source inspection explains two gaps in 0.24:
https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/widget/AbsSeekBar.java

- updateThumbAndTrackPos limits track thickness to min(mMaxHeight,paddedHeight).
  Changing the rotated View's layout size alone does not remove this limit.
- drawableStateChanged can set alpha on the entire progress drawable, undoing
  the styler's alpha=0 on the background child and revealing its native stroke.

The inspected framework is AOSP, not an extraction of the phone's framework.jar;
these mechanisms fit the screenshot and the native SeekBar inheritance, but
runtime acceptance is still required.

Fixes:
- Match native minHeight/maxHeight to the new capsule width before relayout.
- Remove cross-axis padding only; preserve longitudinal touch/level coordinates.
- Apply a transparent progress-background tint with explicit SRC_IN. A subsequent
  drawable alpha reset cannot turn transparent tinted pixels into an outline.
- Keep all native progress objects, warning animations and controls.
- Remove the custom inset top arc. The single perimeter gradient supplies light
  at the ends, matching the reference more closely.
- Restore original height limits, padding and background tint at detach/failure.

Validation: signed assembleDebug CI. Device check: 0%, 50%, 100%, press/release,
reopen, expand, mute, and compare over a colorful backdrop. Expect one perimeter
at zero and full-width native fill at nonzero levels. main/signing unchanged.
