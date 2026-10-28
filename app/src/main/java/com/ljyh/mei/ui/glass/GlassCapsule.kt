package com.ljyh.mei.ui.glass

import com.kyant.shapes.Capsule

/**
 * A single stateless capsule instance shared by every glass surface.
 *
 * The shadow and refraction renderers key their cached masks on the shape
 * object itself (see [NavigationInnerShadowRenderer.GeometryKey]). Creating a
 * new [Capsule] per draw frame therefore discards those masks and forces the
 * outline, dilation union and RenderNode recordings to run again while the
 * bottom bar morphs. [Capsule] has no constructor parameters and its outline
 * is a pure function of the drawn size, so reusing one instance is visually
 * identical and lets the caches stay warm.
 */
internal val GlassCapsule: Capsule = Capsule()
