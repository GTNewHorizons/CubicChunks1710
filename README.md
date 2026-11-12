# Cubic Chunks for 1.7.10

A port of https://github.com/OpenCubicChunks/CubicChunks targeting 1.7.10.

EXPECT MANY BUGS WITH CURRENT BUILDS, AND NO COMPAT TESTING HAS BEEN DONE YET.

Huge thanks to contributors:
RecursivePineapple
DarkShadow44

For helping get the port off the ground.

TODO:
- World Conversions
- Compat Testing - [Link](https://docs.google.com/spreadsheets/d/1Ruar7KJ-upAkgA64MK3t-Yh3VcDsk0b1kO10thN_T9w/edit?usp=sharing)

## Server view distance

`view-distance` in server.properties controls the horizontal radius in 16-block
cubes. The existing `vertical-view-distance` controls the vertical radius;
its default -1 follows `view-distance`. Both effective radii are clamped to 3–32.
Player cube loading is rounded: equal radii form a sphere (radius 8 includes
2,109 cubes), while different radii form an ellipsoid. The client vertical slider
still controls vertical rendering and the integrated server's vertical radius.
Anchors and terrain-generation/lighting dependencies can retain additional cubes
outside a player's view.
