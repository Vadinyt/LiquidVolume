# 0.26: native decorative outlines and media speaker artwork

2318.jpg still shows a narrow inner perimeter after 0.25. The tint fix did not
resolve the observed problem. The supplied volume_row_view XML also contains
independent decorative FrameLayouts 0x7e0a1054 and 0x7e0a1055, using outline
shape drawables at native resource dimensions. Their runtime visibility has not
been established by logs; this remains a targeted candidate, not a proven sole
cause. Neutralize only their GradientDrawable backgrounds using same-type clones
with transparent fill and zero stroke. Preserve View identity, animation,
visibility and layout. Restore their original backgrounds on detach. Log IDs,
visibility and dimensions when changed to distinguish these planes in a future
runtime report if the outline persists.

Add an original vector speaker silhouette with sound waves or a mute slash,
inspired by the supplied iOS reference. It overlays only the native MEDIA icon
button (stream 3); original ImageViews use imageAlpha=0 and restore on detach.
No new window or input View. Preserve native button click and accessibility,
other stream symbols, and original icon animations/state machinery.

VolumeIcon.updateIconState(VolumePanelRow,boolean) and the model getters
getStreamType/isMuted/getRealLevel were verified in the supplied APK DEX.
The hook executes Samsung first, then updates the glyph from the native model.
If no verified media status is available, retain the original icon.
The speaker redraws on state/layout events, without draw hooks or polling.

Build validation: assembleDebug with unchanged persistent signature. Device
acceptance remains necessary for outline removal, speaker/mute transition,
icon tap, hardware volume, drag, and other streams. main remains untouched.
