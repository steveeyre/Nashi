package com.nashi.feature.space

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.google.android.filament.MaterialInstance
import com.nashi.core.model.Item
import com.nashi.core.model.StorageSpace
import com.nashi.core.optimizer.SpaceOptimizer
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.Scene
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader

/**
 * 空间的 3D 预览: 地板+背板+侧板勾勒柜体, 物品按优化后的坐标/尺寸摆成方块。
 * 尺寸单位沿用 cm, 1 cm = 1 filament 单位; 相机自动框住整个空间。
 */
@Composable
fun Space3DView(
    space: StorageSpace,
    items: List<Item>,
    result: SpaceOptimizer.Result?,
    modifier: Modifier = Modifier,
) {
    // 内容变化时重建整棵 3D 子树, 避免在旧场景上叠加方块
    key(space.id, items, result) {
        Space3DScene(space, items, result, modifier)
    }
}

@Composable
private fun Space3DScene(
    space: StorageSpace,
    items: List<Item>,
    result: SpaceOptimizer.Result?,
    modifier: Modifier,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberCameraNode(engine)

    val frameColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val goodsColor = MaterialTheme.colorScheme.primary.toArgb()
    val placedColor = MaterialTheme.colorScheme.tertiary.toArgb()

    val frameMat: MaterialInstance = remember(materialLoader, frameColor) {
        materialLoader.createColorInstance(frameColor)
    }
    val goodsMat: MaterialInstance = remember(materialLoader, goodsColor) {
        materialLoader.createColorInstance(goodsColor)
    }
    val placedMat: MaterialInstance = remember(materialLoader, placedColor) {
        materialLoader.createColorInstance(placedColor)
    }

    val w = space.width.takeIf { it > 0f } ?: 100f
    val d = space.depth.takeIf { it > 0f } ?: 60f
    val shelfH = space.shelfHeight.takeIf { it > 0f } ?: 40f
    val columns = maxOf(1, space.columns)
    val shelfW = w / columns
    val totalH = shelfH * 2f

    val nodes: List<Node> = remember(engine, space.id, items, result, frameMat, goodsMat, placedMat) {
        val built = mutableListOf<Node>()

        fun cube(size: Float3, center: Float3, mat: MaterialInstance, name: String) {
            built += CubeNode(engine, size, Float3(0f), mat).apply {
                this.name = name
                position = center
            }
        }

        cube(Float3(w, 1f, d), Float3(w / 2f, -0.5f, d / 2f), frameMat, "floor")
        cube(Float3(w, totalH, 1f), Float3(w / 2f, totalH / 2f, -0.5f), frameMat, "back")
        cube(Float3(1f, totalH, d), Float3(-0.5f, totalH / 2f, d / 2f), frameMat, "left")
        cube(Float3(1f, totalH, d), Float3(w + 0.5f, totalH / 2f, d / 2f), frameMat, "right")
        for (i in 1 until columns) {
            cube(Float3(1f, totalH, d), Float3(i * shelfW, totalH / 2f, d / 2f), frameMat, "divider-$i")
        }

        if (result != null) {
            result.placed.forEach { p ->
                val col = p.shelfId.substringAfterLast("-").toIntOrNull() ?: 0
                cube(
                    Float3(p.rw.coerceAtLeast(2f), p.rh.coerceAtLeast(2f), p.rd.coerceAtLeast(2f)),
                    Float3(col * shelfW + p.x + p.rw / 2f, p.y + p.rh / 2f, p.z + p.rd / 2f),
                    placedMat,
                    p.box.id,
                )
            }
        } else {
            items.forEach { item ->
                val iw = item.width.takeIf { it > 0f } ?: 20f
                val ih = item.height.takeIf { it > 0f } ?: 20f
                val idp = item.depth.takeIf { it > 0f } ?: 20f
                cube(
                    Float3(iw, ih, idp),
                    Float3(item.x + iw / 2f, item.y + ih / 2f, item.z + idp / 2f),
                    goodsMat,
                    item.id,
                )
            }
        }
        built
    }

    LaunchedEffect(cameraNode, space.id) {
        cameraNode.lookAt(
            Float3(w * 0.5f, totalH * 1.1f, d * 2f + 90f),
            Float3(w / 2f, totalH / 2f, d / 2f),
            Float3(0f, 1f, 0f),
        )
    }

    Scene(
        modifier = modifier.fillMaxWidth().height(260.dp),
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        childNodes = nodes,
    )
}
