# 0.24: implement the user's supplied smoked-glass reference

Reference: 2299.jpg. User explicitly requested this appearance after 0.23.
Target a capsule about 3.1 times as tall as it is wide, neutral smoked glass,
subtle light at the ends, dark side edges, a light native bottom icon, and
translucent native volume fill. Preserve a discreet visible expand button.

The compact track and existing blur sibling now receive matching explicit
width/height, keeping native height and right-side window anchoring. The native
270-degree SeekBar gets the swapped dimensions (width = capsule height,
height = capsule width), preserving its horizontal level/touch coordinate
system before rotation. No view scaling, touch hook or audio reimplementation.
One layout callback refreshes the blur radius and artwork after layout; it is
removed immediately or during rollback. Original layout sizes and icon tints
are restored with the rest of the style. Unsupported rotation/layout fails closed.

The shell uses neutral tint and a diffuse white reflection, no blue pearl
surface. Rim shading is light/dark/dark/light from top to bottom. Preview uses
the same shell/rim and target proportions, but is not device validation.
The 0.23 fill-opacity behavior remains. Icon silhouettes remain Samsung's
stream-specific icons; this is an inspired implementation, not a pixel copy.

Build validation: GitHub Actions assembleDebug using unchanged signing config.
Device acceptance still needed: no edge clipping, one aligned capsule, correct
bottom/mid/top drag levels, hardware keys, expand, mute, reopen, warning state,
and appearance over both colored and black backgrounds. Runtime icon updates
and Samsung's window/animation behavior must also be checked on the S23+.
main remains the rollback baseline. No framework binary or signing key changes.
