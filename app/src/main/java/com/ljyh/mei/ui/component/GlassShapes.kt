package com.ljyh.mei.ui.component

import com.kyant.shapes.Capsule

/**
 * 与 glasmio 内部的 `ContactCapsule` 同理：胶囊没有构造参数，轮廓是绘制尺寸的
 * 纯函数，因此复用同一个实例可以让阴影 / 折射渲染器按 shape 实例缓存的遮罩保持
 * 命中；每帧 new 一个 `Capsule()` 会让这些遮罩缓存全部失效。
 */
internal val SharedGlassCapsule: Capsule = Capsule()
