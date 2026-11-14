# 0.23: preserve the backdrop through the native fill

User reports that 0.22 controls work normally. 2290.jpg (muted) shows a dark
capsule with rim highlights; 2292.jpg shows a nearly solid white filled capsule.
This is consistent with styled background plus opaque native progress. Images
alone do not prove runtime hook execution or actual GPU backdrop blur.

Inspected SecRoundedCornerSeekBarDrawable DEX in the same SystemUI APK identified
in native-glass-0.22.md:

- setContext sets endColor alpha to 255.
- onLevelChange rewrites the nested GradientDrawable colors from gradientColor
  and endColor, then changes its bounds to match the volume level.
- setShockColor animates gradientColor towards endColor at the maximum/warning
  condition. This can make the entire native fill opaque.
- draw also creates separate Paint objects for the low-level cap and touch button.
  Setting alpha on the child drawable would not cover all of those paintings.
- VolumeSeekBar.setEnabled writes View.alpha to 1 or 0.7.

Compose the existing seekbar view at 0.62 times its native alpha, retaining 38%
of the underlying material even where its own fill is opaque. This is a material
choice, not a measured replica of iOS. Neither the background shell nor sibling
icons are attenuated. No native drawable replacement, color rewrite, touch hook,
level hook, draw hook, or new window. Preserve the native disabled multiplier
with an after-setEnabled hook restricted to already styled views; restore the
latest native alpha at detach. Repeated application does not multiply twice.

Device acceptance: compare 0%, 50%, 100% over the SAME colored/text background;
check a moving backdrop to distinguish blur from a static gray gradient. Check
drag, mute, expand, dismiss/reopen and native high-volume indication. Compilation
cannot prove these. If the result remains unchanged, request the 0.23 LSPosed
logs (GLASS_COMPOSITION_APPLIED includes fillViewAlpha), not another blind edit.
