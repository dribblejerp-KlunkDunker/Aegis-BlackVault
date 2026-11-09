package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.MindMapEngine
import com.example.model.*
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.*

private data class ProjectedHierarchyNode(
    val entity: MindMapHierarchyEntity,
    val category: NodeCategory,
    val screenX: Float,
    val screenY: Float,
    val scale: Float,
    val radiusPx: Float,
    val color: Color
)

@Composable
fun InteractiveMindMapSurface(
    activeSubject: SubjectProfile,
    allSubjects: List<SubjectProfile>,
    triplets: List<KnowledgeTripletEntity>,
    mindMapHierarchyEntities: List<MindMapHierarchyEntity> = emptyList(),
    linguisticProfileEntities: List<LinguisticPersonalityProfileEntity> = emptyList(),
    graphMode: String,
    onGraphModeChange: (String) -> Unit,
    onSelectSubject: (String) -> Unit,
    onNavigateToPsychTab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Retrieve Room-persisted hierarchical edges for the active subject, with deterministic fallback
    val subjectHierarchyEntities = remember(activeSubject, allSubjects, triplets, mindMapHierarchyEntities) {
        val fromRoom = mindMapHierarchyEntities.filter { it.rootSubjectId == activeSubject.id }
        if (fromRoom.isNotEmpty()) {
            fromRoom.sortedWith(compareBy<MindMapHierarchyEntity> { it.hierarchyDepth }.thenBy { it.hierarchyId })
        } else {
            MindMapEngine.buildMindMapHierarchyEntities(activeSubject, allSubjects, triplets)
        }
    }

    // Retrieve Room-persisted LinguisticPersonalityProfileEntity for the active or linked subject
    val activeRoomProfileEntity = remember(activeSubject.id, linguisticProfileEntities) {
        linguisticProfileEntities.find { it.subjectId == activeSubject.id }
            ?: activeSubject.toLinguisticProfileEntity()
    }

    var selectedCategoryFilter by remember { mutableStateOf<NodeCategory?>(null) }
    var selectedDepthFilter by remember { mutableStateOf<Int?>(null) } // null = All, 1 = Depth 0..1 Hubs, 2 = Depth 2 Leaves
    var selectedEntity by remember(activeSubject.id, subjectHierarchyEntities.size) {
        mutableStateOf<MindMapHierarchyEntity?>(
            subjectHierarchyEntities.firstOrNull { it.hierarchyDepth == 0 } ?: subjectHierarchyEntities.firstOrNull()
        )
    }
    var inspectorTab by remember { mutableIntStateOf(0) } // 0 = Psychometrics & Node Intel, 1 = What Makes Them Tick, 2 = Talk & Leverage

    var autoRotate by remember { mutableStateOf(graphMode != "HIERARCHY") }
    var rotY by remember { mutableFloatStateOf(0.35f) }
    var rotX by remember { mutableFloatStateOf(0.18f) }
    var zoom by remember { mutableFloatStateOf(1.08f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val infiniteTransition = rememberInfiniteTransition(label = "mindmap_pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "edge_pulse"
    )

    LaunchedEffect(autoRotate, graphMode) {
        while (autoRotate && (graphMode == "FORCE" || graphMode == "UMAP")) {
            withFrameNanos {
                rotY += 0.0042f
            }
        }
    }

    // Filter hierarchical entities while always preserving ancestor path (Root + Parent Hub) so hierarchical edges remain connected
    val filteredEntities = remember(subjectHierarchyEntities, selectedCategoryFilter, selectedDepthFilter) {
        val byNode = subjectHierarchyEntities.associateBy { it.childNodeId }
        val matching = subjectHierarchyEntities.filter { ent ->
            val cat = parseNodeCategory(ent.nodeCategory)
            val catOk = selectedCategoryFilter == null || ent.hierarchyDepth == 0 || cat == selectedCategoryFilter
            val depthOk = when (selectedDepthFilter) {
                null -> true
                1 -> ent.hierarchyDepth <= 1
                2 -> ent.hierarchyDepth == 0 || ent.hierarchyDepth == 2
                else -> true
            }
            catOk && depthOk
        }
        val requiredIds = matching.map { it.childNodeId }.toMutableSet()
        matching.forEach { ent ->
            var currParent = ent.parentNodeId
            while (currParent != "ROOT" && currParent.isNotBlank()) {
                requiredIds.add(currParent)
                currParent = byNode[currParent]?.parentNodeId ?: "ROOT"
            }
        }
        subjectHierarchyEntities.filter { it.childNodeId in requiredIds }
    }

    // Generate deterministic Gaussian nebula particles around clusters
    val nebulaParticles = remember(subjectHierarchyEntities) {
        val list = mutableListOf<Triple<Float, Float, Float>>()
        if (subjectHierarchyEntities.isNotEmpty()) {
            for (i in 0 until 110) {
                val anchor = subjectHierarchyEntities[i % subjectHierarchyEntities.size]
                val angle1 = (i * 1.618f) * 6.28f
                val angle2 = (i * 0.754f) * 6.28f
                val r = 14f + (i % 7) * 6f
                list.add(
                    Triple(
                        anchor.x3d + cos(angle1) * r,
                        anchor.y3d + sin(angle1) * r,
                        anchor.z3d + cos(angle2) * r
                    )
                )
            }
        }
        list
    }

    val textMeasurer = rememberTextMeasurer()
    var latestProjected by remember { mutableStateOf<List<ProjectedHierarchyNode>>(emptyList()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Top Controls Bar: Projection Mode Switcher (HIERARCHY, FORCE, RADIAL, UMAP) + Zoom/Orbit Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianPanel, RoundedCornerShape(4.dp))
                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = "Room Hierarchical Graph",
                    tint = PhosphorGreen,
                    modifier = Modifier.size(16.dp)
                )
                listOf("HIERARCHY", "FORCE", "RADIAL", "UMAP").forEach { mode ->
                    val isSelected = graphMode == mode
                    Box(
                        modifier = Modifier
                            .testTag("mindmap_mode_$mode")
                            .background(
                                if (isSelected) PhosphorGreen else ObsidianBg,
                                RoundedCornerShape(3.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) PhosphorGreen else PhosphorGreenDim,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable {
                                onGraphModeChange(mode)
                                if (mode == "HIERARCHY" || mode == "RADIAL") {
                                    autoRotate = false
                                    rotX = 0f
                                    rotY = 0f
                                }
                            }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = mode,
                            color = if (isSelected) ObsidianBg else PhosphorGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { autoRotate = !autoRotate },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (autoRotate) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Orbit",
                        tint = AmberSignal,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = { zoom = (zoom * 1.18f).coerceAtMost(2.5f) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom In",
                        tint = PhosphorGreen,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = { zoom = (zoom / 1.18f).coerceAtLeast(0.55f) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom Out",
                        tint = PhosphorGreen,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(
                    onClick = {
                        zoom = 1.08f
                        panOffset = Offset.Zero
                        rotX = 0.18f
                        rotY = 0.35f
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "Reset View",
                        tint = CyanTelemetry,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // 2. Hierarchy Depth & Category Filter Pills
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterPill(
                    label = "ALL DEPTHS (${subjectHierarchyEntities.size})",
                    color = PhosphorGreen,
                    selected = selectedCategoryFilter == null && selectedDepthFilter == null,
                    onClick = {
                        selectedCategoryFilter = null
                        selectedDepthFilter = null
                    }
                )
            }
            item {
                FilterPill(
                    label = "DEPTH 0–1 HUBS",
                    color = AmberSignal,
                    selected = selectedDepthFilter == 1,
                    onClick = {
                        selectedDepthFilter = if (selectedDepthFilter == 1) null else 1
                    }
                )
            }
            items(NodeCategory.entries) { cat ->
                FilterPill(
                    label = cat.label,
                    color = categoryColor(cat),
                    selected = selectedCategoryFilter == cat,
                    onClick = {
                        selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                    }
                )
            }
        }

        // 3. Compose Canvas Hierarchical & 3D Brain Graph Visualization
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF040605), RoundedCornerShape(6.dp))
                .border(1.dp, PhosphorGreenDim, RoundedCornerShape(6.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("interactive_mind_map_canvas")
                    .pointerInput(graphMode) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (graphMode == "HIERARCHY" || graphMode == "RADIAL") {
                                panOffset += Offset(dragAmount.x, dragAmount.y)
                            } else {
                                rotY += dragAmount.x * 0.007f
                                rotX = (rotX + dragAmount.y * 0.005f).coerceIn(-1.1f, 1.1f)
                            }
                        }
                    }
                    .pointerInput(graphMode) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Scroll) {
                                    val scrollDeltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (scrollDeltaY != 0f) {
                                        val zoomFactor = if (scrollDeltaY < 0f) 1.10f else 0.90f
                                        zoom = (zoom * zoomFactor).coerceIn(0.65f, 2.35f)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(latestProjected) {
                        detectTapGestures { tapOffset ->
                            val hit = latestProjected.minByOrNull {
                                hypot(it.screenX - tapOffset.x, it.screenY - tapOffset.y)
                            }
                            if (hit != null &&
                                hypot(hit.screenX - tapOffset.x, hit.screenY - tapOffset.y) <= max(40f, hit.radiusPx + 20f)
                            ) {
                                selectedEntity = hit.entity
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val cx = w / 2f + panOffset.x
                val cy = h / 2f + panOffset.y
                val ringColor = PhosphorGreen.copy(alpha = 0.08f)

                val projectedMap = linkedMapOf<String, ProjectedHierarchyNode>()

                if (graphMode == "HIERARCHY") {
                    // Draw 3-tier horizontal hierarchy depth guide lines (Depth 0, Depth 1, Depth 2)
                    val yDepth0 = (h * 0.15f) * zoom + panOffset.y
                    val yDepth1 = (h * 0.46f) * zoom + panOffset.y
                    val yDepth2 = (h * 0.80f) * zoom + panOffset.y

                    listOf(
                        yDepth0 to "DEPTH 0 // IDENTITY ROOT",
                        yDepth1 to "DEPTH 1 // CLUSTER & PSYCHOMETRIC HUBS",
                        yDepth2 to "DEPTH 2 // ARTIFACTS & TRAIT LEAVES"
                    ).forEach { (tierY, tierLabel) ->
                        drawLine(
                            color = ringColor,
                            start = Offset(12f, tierY),
                            end = Offset(w - 12f, tierY),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        val tierLayout = textMeasurer.measure(
                            text = tierLabel,
                            style = TextStyle(
                                color = PhosphorGreenDim.copy(alpha = 0.7f),
                                fontSize = 8.sp,
                                fontFamily = ShareTechMonoFamily
                            )
                        )
                        drawText(tierLayout, topLeft = Offset(10f, (tierY - 14f).coerceIn(2f, h - 14f)))
                    }

                    val depth0Nodes = filteredEntities.filter { it.hierarchyDepth == 0 }
                    val depth1Nodes = filteredEntities.filter { it.hierarchyDepth == 1 }
                    val depth2Nodes = filteredEntities.filter { it.hierarchyDepth >= 2 }

                    // Position Depth-0 Root
                    depth0Nodes.forEachIndexed { idx, ent ->
                        val cat = parseNodeCategory(ent.nodeCategory)
                        val xPos = (w / (depth0Nodes.size + 1)) * (idx + 1) + panOffset.x
                        projectedMap[ent.childNodeId] = ProjectedHierarchyNode(
                            entity = ent,
                            category = cat,
                            screenX = xPos,
                            screenY = yDepth0,
                            scale = 1.25f * zoom,
                            radiusPx = (16f * zoom).coerceIn(10f, 26f),
                            color = categoryColor(cat)
                        )
                    }

                    // Position Depth-1 Hubs evenly across tier 1
                    depth1Nodes.forEachIndexed { idx, ent ->
                        val cat = parseNodeCategory(ent.nodeCategory)
                        val span = w - 48f
                        val step = if (depth1Nodes.size > 1) span / (depth1Nodes.size - 1) else 0f
                        val xPos = if (depth1Nodes.size == 1) w / 2f else 24f + idx * step
                        val yJitter = if (idx % 2 == 1) 14f * zoom else -8f * zoom
                        projectedMap[ent.childNodeId] = ProjectedHierarchyNode(
                            entity = ent,
                            category = cat,
                            screenX = xPos + panOffset.x,
                            screenY = yDepth1 + yJitter,
                            scale = 1.05f * zoom,
                            radiusPx = (12.5f * zoom).coerceIn(8f, 22f),
                            color = categoryColor(cat)
                        )
                    }

                    // Order Depth-2 Leaves by their parent Depth-1 Hub's X coordinate to minimize edge crossings
                    val sortedDepth2 = depth2Nodes.sortedBy { leaf ->
                        projectedMap[leaf.parentNodeId]?.screenX ?: (w / 2f)
                    }
                    sortedDepth2.forEachIndexed { idx, ent ->
                        val cat = parseNodeCategory(ent.nodeCategory)
                        val span = w - 36f
                        val step = if (sortedDepth2.size > 1) span / (sortedDepth2.size - 1) else 0f
                        val xPos = if (sortedDepth2.size == 1) w / 2f else 18f + idx * step
                        val yOffset = when (idx % 3) {
                            0 -> -18f * zoom
                            1 -> 6f * zoom
                            else -> 26f * zoom
                        }
                        projectedMap[ent.childNodeId] = ProjectedHierarchyNode(
                            entity = ent,
                            category = cat,
                            screenX = xPos + panOffset.x,
                            screenY = (yDepth2 + yOffset).coerceIn(48f, h - 22f),
                            scale = 0.95f * zoom,
                            radiusPx = (9.5f * zoom).coerceIn(7f, 18f),
                            color = categoryColor(cat)
                        )
                    }
                } else {
                    // 3D / Radial / UMAP Projection
                    val fov = min(w, h) * 0.92f * zoom
                    val camZ = 340f
                    val rInner = min(w, h) * 0.21f * zoom
                    val rOuter = min(w, h) * 0.39f * zoom

                    drawCircle(ringColor, radius = rInner, center = Offset(cx, cy), style = Stroke(1f))
                    drawCircle(ringColor, radius = rOuter, center = Offset(cx, cy), style = Stroke(1f))
                    drawLine(ringColor, Offset(0f, cy), Offset(w, cy), strokeWidth = 1f)
                    drawLine(ringColor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1f)

                    val cosY = cos(rotY)
                    val sinY = sin(rotY)
                    val cosX = cos(rotX)
                    val sinX = sin(rotX)

                    fun project3D(x: Float, y: Float, z: Float): Triple<Float, Float, Float>? {
                        val x1 = x * cosY - z * sinY
                        val z1 = z * cosY + x * sinY
                        val y2 = y * cosX - z1 * sinX
                        val z2 = z1 * cosX + y * sinX
                        val depth = camZ + z2
                        if (depth <= 20f) return null
                        val scale = fov / depth
                        return Triple(cx + x1 * scale, cy + y2 * scale, scale)
                    }

                    // Render Gaussian Nebula Background Particles
                    nebulaParticles.forEachIndexed { idx, (nx, ny, nz) ->
                        val proj = project3D(nx, ny, nz) ?: return@forEachIndexed
                        val pColor = if (idx % 3 == 0) {
                            CyanTelemetry
                        } else if (idx % 2 == 0) {
                            PhosphorGreen
                        } else {
                            AmberSignal
                        }
                        drawCircle(
                            color = pColor.copy(alpha = 0.16f),
                            radius = (1.5f * proj.third).coerceIn(1f, 3.8f),
                            center = Offset(proj.first, proj.second)
                        )
                    }

                    val depth1List = filteredEntities.filter { it.hierarchyDepth == 1 }
                    val depth2List = filteredEntities.filter { it.hierarchyDepth >= 2 }

                    filteredEntities.forEach { ent ->
                        val cat = parseNodeCategory(ent.nodeCategory)
                        val (bx, by, bz) = when (graphMode) {
                            "UMAP" -> Triple(ent.umapX, ent.umapY, ent.umapZ)

                            "RADIAL" -> {
                                when (ent.hierarchyDepth) {
                                    0 -> Triple(0f, 0f, 0f)

                                    1 -> {
                                        val idx1 = depth1List.indexOf(ent).coerceAtLeast(0)
                                        val ang = (idx1 * 2 * Math.PI / max(1, depth1List.size)).toFloat()
                                        Triple(cos(ang) * 74f, sin(ang) * 74f, 0f)
                                    }

                                    else -> {
                                        val idx2 = depth2List.indexOf(ent).coerceAtLeast(0)
                                        val ang = (idx2 * 2 * Math.PI / max(1, depth2List.size)).toFloat()
                                        Triple(cos(ang) * 142f, sin(ang) * 142f, 0f)
                                    }
                                }
                            }

                            else -> Triple(ent.x3d, ent.y3d, ent.z3d)
                        }
                        val proj = project3D(bx, by, bz) ?: return@forEach
                        val rPx = (ent.nodeWeight * 8.2f * proj.third).coerceIn(6.5f, 25f)
                        projectedMap[ent.childNodeId] = ProjectedHierarchyNode(
                            entity = ent,
                            category = cat,
                            screenX = proj.first,
                            screenY = proj.second,
                            scale = proj.third,
                            radiusPx = rPx,
                            color = categoryColor(cat)
                        )
                    }
                }

                latestProjected = projectedMap.values.toList()

                // 3. Draw Hierarchical Parent -> Child Edges from Room (`parentNodeId -> childNodeId`)
                filteredEntities.forEach { ent ->
                    if (ent.parentNodeId == "ROOT") return@forEach
                    val src = projectedMap[ent.parentNodeId]
                        ?: projectedMap.values.firstOrNull { it.entity.hierarchyDepth == 0 }
                        ?: return@forEach
                    val dst = projectedMap[ent.childNodeId] ?: return@forEach

                    val isHighlighted = selectedEntity?.childNodeId == ent.childNodeId ||
                        selectedEntity?.childNodeId == ent.parentNodeId
                    val edgeColor = if (isHighlighted) {
                        dst.color.copy(alpha = 0.88f)
                    } else {
                        dst.color.copy(alpha = 0.36f)
                    }

                    if (graphMode == "HIERARCHY") {
                        // Draw smooth cubic Bezier curve from parent tier to child tier
                        val midY = (src.screenY + dst.screenY) / 2f
                        val bezierPath = Path().apply {
                            moveTo(src.screenX, src.screenY)
                            cubicTo(
                                src.screenX,
                                midY,
                                dst.screenX,
                                midY,
                                dst.screenX,
                                dst.screenY
                            )
                        }
                        drawPath(
                            path = bezierPath,
                            color = edgeColor,
                            style = Stroke(
                                width = if (isHighlighted) 2.5f else 1.3f,
                                pathEffect = if (ent.confidenceWeight < 0.85f) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                            )
                        )
                    } else {
                        drawLine(
                            color = edgeColor,
                            start = Offset(src.screenX, src.screenY),
                            end = Offset(dst.screenX, dst.screenY),
                            strokeWidth = if (isHighlighted) 2.4f else 1.2f,
                            pathEffect = if (ent.confidenceWeight < 0.85f) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                        )
                    }

                    // Synaptic pulse travelling from parentNodeId -> childNodeId
                    val px = src.screenX + (dst.screenX - src.screenX) * pulsePhase
                    val py = src.screenY + (dst.screenY - src.screenY) * pulsePhase
                    drawCircle(
                        color = if (isHighlighted) AmberSignal else dst.color,
                        radius = if (isHighlighted) 4.0f else 2.4f,
                        center = Offset(px, py)
                    )

                    // Draw predicate badge on highlighted edge
                    if (isHighlighted) {
                        val mx = (src.screenX + dst.screenX) / 2f
                        val my = (src.screenY + dst.screenY) / 2f
                        val predLayout = textMeasurer.measure(
                            text = ent.relationshipPredicate.take(16),
                            style = TextStyle(
                                color = AmberSignal,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ShareTechMonoFamily
                            )
                        )
                        drawText(
                            predLayout,
                            topLeft = Offset(
                                (mx - predLayout.size.width / 2f).coerceIn(4f, (w - predLayout.size.width - 4f).coerceAtLeast(4f)),
                                (my - predLayout.size.height / 2f).coerceIn(4f, (h - predLayout.size.height - 4f).coerceAtLeast(4f))
                            )
                        )
                    }
                }

                // 4. Draw Hierarchy Nodes + Depth Rings + Labels
                projectedMap.values.sortedBy { it.scale }.forEach { pn ->
                    val isSel = selectedEntity?.childNodeId == pn.entity.childNodeId
                    val isHub = pn.entity.hierarchyDepth == 1
                    val isRoot = pn.entity.hierarchyDepth == 0

                    if (isSel) {
                        drawCircle(
                            color = pn.color.copy(alpha = 0.28f),
                            radius = pn.radiusPx * 2.0f,
                            center = Offset(pn.screenX, pn.screenY)
                        )
                    }
                    if (isHub || isRoot) {
                        drawCircle(
                            color = pn.color.copy(alpha = 0.14f),
                            radius = pn.radiusPx * 1.45f,
                            center = Offset(pn.screenX, pn.screenY),
                            style = Stroke(width = 1f)
                        )
                    }

                    drawCircle(
                        color = ObsidianBg,
                        radius = pn.radiusPx,
                        center = Offset(pn.screenX, pn.screenY)
                    )
                    drawCircle(
                        color = if (isSel) AmberSignal else pn.color,
                        radius = pn.radiusPx,
                        center = Offset(pn.screenX, pn.screenY),
                        style = Stroke(
                            width = if (isSel) {
                                3.2f
                            } else if (isRoot || isHub) {
                                2.2f
                            } else {
                                1.6f
                            }
                        )
                    )
                    drawCircle(
                        color = pn.color,
                        radius = pn.radiusPx * 0.46f,
                        center = Offset(pn.screenX, pn.screenY)
                    )

                    val labelLayout = textMeasurer.measure(
                        text = pn.entity.childLabel.take(18),
                        style = TextStyle(
                            color = if (isSel) AmberSignal else TextPrimary,
                            fontSize = if (isRoot) {
                                11.sp
                            } else if (isHub) {
                                9.5.sp
                            } else {
                                8.5.sp
                            },
                            fontWeight = if (isSel || isRoot || isHub) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = ShareTechMonoFamily
                        )
                    )
                    val lx = (pn.screenX + pn.radiusPx + 4f).coerceIn(4f, (w - labelLayout.size.width - 4f).coerceAtLeast(4f))
                    val ly = (pn.screenY - labelLayout.size.height / 2f).coerceIn(4f, (h - labelLayout.size.height - 4f).coerceAtLeast(4f))
                    drawText(labelLayout, topLeft = Offset(lx, ly))
                }
            }

            // Overlay Legend inside Top-Left of Canvas
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(ObsidianPanel.copy(alpha = 0.88f), RoundedCornerShape(4.dp))
                    .border(1.dp, PhosphorGreenDim, RoundedCornerShape(4.dp))
                    .padding(6.dp)
            ) {
                Text(
                    text = "ROOM HIERARCHY GRAPH // ${activeSubject.handle} ($graphMode)",
                    color = PhosphorGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap any node for Psychometrics • ${filteredEntities.size} Room Nodes (D0–D2)",
                    color = TextSecondary,
                    fontSize = 9.sp
                )
            }
        }

        // 4. Quick-Tap Room Hierarchy Node Selector Strip (Accessible & Testable)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mindmap_hierarchy_node_strip"),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredEntities, key = { it.hierarchyId }) { ent ->
                val cat = parseNodeCategory(ent.nodeCategory)
                val c = categoryColor(cat)
                val isSel = selectedEntity?.childNodeId == ent.childNodeId
                Box(
                    modifier = Modifier
                        .testTag("hierarchy_node_chip_${ent.childNodeId}")
                        .background(
                            if (isSel) c.copy(alpha = 0.25f) else ObsidianPanel,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (isSel) AmberSignal else c.copy(alpha = 0.55f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { selectedEntity = ent }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "D${ent.hierarchyDepth}",
                            color = AmberSignal,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = ent.childLabel.take(18),
                            color = if (isSel) TextPrimary else TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // 5. Interactive Room Node & Psychometric Details Inspector Panel
        selectedEntity?.let { ent ->
            val cat = parseNodeCategory(ent.nodeCategory)
            val nodeColor = categoryColor(cat)
            // Resolve the psychometric profile for the tapped node's linked subject (or activeSubject)
            val targetSubj = allSubjects.find { it.id == ent.linkedSubjectId } ?: activeSubject
            val roomProfile = linguisticProfileEntities.find { it.subjectId == targetSubj.id }
                ?: activeRoomProfileEntity
            val domainProfile = roomProfile.toDomainProfile()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 175.dp, max = 235.dp)
                    .testTag("mindmap_psychometric_inspector")
                    .background(ObsidianPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, nodeColor.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header: Node Category, Label, Depth & Parent->Child Relationship
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(nodeColor.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                .border(1.dp, nodeColor, RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DEPTH ${ent.hierarchyDepth} • ${cat.label}",
                                color = nodeColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text(
                                text = ent.childLabel,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${ent.parentNodeId} ➔ [${ent.relationshipPredicate} ${(ent.confidenceWeight * 100).roundToInt()}%]",
                                color = AmberSignal,
                                fontSize = 9.sp
                            )
                        }
                    }

                    if (ent.linkedSubjectId != null && ent.linkedSubjectId != activeSubject.id) {
                        Button(
                            onClick = { onSelectSubject(ent.linkedSubjectId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberSignal,
                                contentColor = ObsidianBg
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("inspector_switch_dossier_btn")
                        ) {
                            Text("SWITCH DOSSIER", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .testTag("inspector_open_psych_btn")
                                .background(VioletHf.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, VioletHf, RoundedCornerShape(4.dp))
                                .clickable { onNavigateToPsychTab() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = "Full Psychometrics",
                                    tint = VioletHf,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "FULL PSYCH HUD",
                                    color = VioletHf,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Sub-tabs inside Node Inspector: Psychometrics vs. What Makes Them Tick vs. Talk & Leverage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0 to "OCEAN & LIWC MATRIX",
                        1 to "WHAT MAKES THEM TICK",
                        2 to "HOW TO TALK & LEVERAGE"
                    ).forEach { (idx, title) ->
                        val isSel = inspectorTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("inspector_tab_$idx")
                                .background(
                                    if (isSel) CyanTelemetry.copy(alpha = 0.2f) else ObsidianBg,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSel) CyanTelemetry else PhosphorGreenDim,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { inspectorTab = idx }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSel) CyanTelemetry else TextSecondary,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Scrollable Inspector Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (inspectorTab) {
                        0 -> {
                            // Node Detail + Room Big Five OCEAN, Dark Triad & LIWC Matrix
                            Text(
                                text = ent.nodeDetails,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MiniPsychBadge("O", roomProfile.openness, CyanTelemetry, Modifier.weight(1f))
                                MiniPsychBadge("C", roomProfile.conscientiousness, PhosphorGreen, Modifier.weight(1f))
                                MiniPsychBadge("E", roomProfile.extraversion, AmberSignal, Modifier.weight(1f))
                                MiniPsychBadge("A", roomProfile.agreeableness, VioletHf, Modifier.weight(1f))
                                MiniPsychBadge("N", roomProfile.neuroticism, AlarmRed, Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "LIWC: ATI=${roomProfile.analyticalThinkingIndex}% | Clout=${roomProfile.cloutDominanceIndex}% | Auth=${roomProfile.authenticityIndex}% | Tone=${roomProfile.emotionalToneIndex}%",
                                    color = PhosphorGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "DARK TRIAD: M=${roomProfile.machiavellianism}% N=${roomProfile.narcissism}% P=${roomProfile.psychopathy}%",
                                    color = AlarmRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        1 -> {
                            Text(
                                text = "ARCHETYPE: ${domainProfile.archetypeTitle} — ${domainProfile.archetypeSummary}",
                                color = AmberSignal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            domainProfile.whatMakesThemTick.forEach { item ->
                                Text(
                                    text = "• [CORE DRIVER]: $item",
                                    color = TextPrimary,
                                    fontSize = 9.5.sp
                                )
                            }
                            domainProfile.egoCravingsAndRewards.take(2).forEach { item ->
                                Text(
                                    text = "• [EGO CRAVING]: $item",
                                    color = PhosphorGreen,
                                    fontSize = 9.5.sp
                                )
                            }
                            domainProfile.fearAndFrictionTriggers.take(2).forEach { item ->
                                Text(
                                    text = "• [FRICTION TRIGGER]: $item",
                                    color = AlarmRed,
                                    fontSize = 9.5.sp
                                )
                            }
                        }

                        else -> {
                            domainProfile.howToTalkToThem.take(2).forEach { item ->
                                Text(
                                    text = "• [RAPPORT PROTOCOL]: $item",
                                    color = CyanTelemetry,
                                    fontSize = 9.5.sp
                                )
                            }
                            domainProfile.exactPhrasesToUse.firstOrNull()?.let { phrase ->
                                Text(
                                    text = "• [OPENER]: $phrase",
                                    color = PhosphorGreen,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            domainProfile.persuasionAndLeverageHooks.take(2).forEach { hook ->
                                Text(
                                    text = "• [LEVERAGE VECTOR]: $hook",
                                    color = AmberSignal,
                                    fontSize = 9.5.sp
                                )
                            }
                            domainProfile.cognitiveBiasesToExploit.take(2).forEach { bias ->
                                Text(
                                    text = "• [BIAS EXPLOIT]: $bias",
                                    color = VioletHf,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPsychBadge(trait: String, score: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(ObsidianBg, RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .padding(vertical = 3.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$trait: $score%",
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FilterPill(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) color.copy(alpha = 0.25f) else ObsidianPanel,
                RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                if (selected) color else color.copy(alpha = 0.4f),
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (selected) color else TextSecondary,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun parseNodeCategory(raw: String): NodeCategory = try {
    NodeCategory.valueOf(raw)
} catch (_: Exception) {
    NodeCategory.OSINT_TOOL
}

fun categoryColor(category: NodeCategory): Color = when (category) {
    NodeCategory.IDENTITY -> AmberSignal
    NodeCategory.GITHUB_REPO -> CyanTelemetry
    NodeCategory.HF_MODEL -> VioletHf
    NodeCategory.PSYCH_TRAIT -> PhosphorGreen
    NodeCategory.GEO_CHRONO -> RustAccent
    NodeCategory.WALLED_GARDEN -> AlarmRed
    NodeCategory.OSINT_TOOL -> Color(0xFF2DD4BF)
}
