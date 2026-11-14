# 0.22: correct the compact row's composition

## Evidence

Inspected the user-supplied SystemUI.apk, package com.android.systemui,
versionName 17, SHA-256:
`d6c164100707987a7dad8405f7c90d1c9af82b62db0444354f0decf778e8bb50`.
This identifies the inspected binary, not proof of the currently installed binary.
The supplied 2282.jpg shows 0.21 with an almost opaque capsule at zero volume.

Decoded XML and DEX establish:

- volume_row_view: blur ImageView 0x7e0a1030 and track FrameLayout 0x7e0a1052
  are aligned siblings. The native 270-degree-rotated VolumeSeekBar 0x7e0a1051
  is the track's child. Icons are separate from the track.
- The blur image has BOTH volume_seekbar_bg_blur as its background AND
  volume_seekbar_fg as its foreground. The old styler did not clear either.
- BlurEffect.setRealTimeBlur(View,int,float,int) constructs SemBlurInfo.Builder(0),
  sets radius 256, background color, a color-curve preset and corner radius,
  sets the foreground alpha to 30, then calls View.semSetBlurInfo.
- When VOLUME_PARTIAL_BLUR is false or reduce-transparency is enabled, that method
  hides the view and clears SemBlurInfo. Checking for BackgroundBlurDrawable in
  View.background cannot detect this backend.
- The progress background in volume_seekbar_drawable_no_blur is already transparent
  apart from its outline. Merely recoloring it does not address the blur-image planes.
- VolumeRowView.initialize assigns native track/progress drawables.
- updateProgressDrawable casts the progress child to SecRoundedCornerSeekBarDrawable
  and calls setShockColor. Replacing that child breaks a real Samsung invariant.

These are code findings. They do not prove which branch or material was active
in the screenshot; no complete 0.21 runtime composition trace was available.

## Change

Use Samsung's live mode 0 on the existing blur view, with neutral background,
no whitening color-curve preset, and a bounded 48–120 px radius. Read the previous
SemBlurInfo for restoration. Missing reflection methods fail before removing
native material. Retaining SemBlurInfo is logged as composition applied, not as
proof of rendered GPU blur.

Remove the native blur background and foreground material. Keep a non-null,
transparent foreground because Samsung dereferences it on its next blur call.
Hide only the native progress background via its alpha; preserve the entire
progress hierarchy, including warning colors and level animation.

Use the existing LiquidGlassShellDrawable and LiquidGlassRimDrawable from the
preview. Remove the 0.21 full-surface overlay. Neither preview nor input logic
changes. Require matching sibling geometry to prevent a second capsule.

Run after the original native blur call and after row initialization, including
when a Samsung feature flag skips blur. Queue one update per row; if needed,
wait for one layout. No draw interception, touch interception or frame loop.
Respect accessibility_reduce_transparency in System/Secure settings.
Restore owned changes independently on failure/detach. Stop attempts after three
composition errors. main and the existing signing configuration are unchanged.

## Validation and device acceptance

The build workflow compiles and signs the APK with the existing repository key.
Compilation alone cannot validate Samsung's rendering backend or touch stability.

On the S23+, test the same screen with text/color behind the capsule at 0%,
about 50%, and 100% volume. Confirm the backdrop is visible and blurred at 0%,
not just a blue-gray fill. Check hardware keys, drag in both directions, mute,
expand, dismiss/reopen and the native high-volume warning. Check reduce-transparency
still yields native behavior. Any crash or lock-screen return fails acceptance.

Useful bounded logs: GLASS_HOOK_INSTALLED, GLASS_ROW_HOOK_INSTALLED,
GLASS_COMPOSITION_APPLIED (backend/mode/dimensions/visibility/inner alpha),
GLASS_UNSUPPORTED_GEOMETRY, GLASS_COMPOSITION_FAILED, GLASS_RESTORE_FAILED.
If acceptance fails, obtain one screenshot and LSPosed/SystemUI logs for this
version before choosing another rendering change. Do not merge into main yet.
