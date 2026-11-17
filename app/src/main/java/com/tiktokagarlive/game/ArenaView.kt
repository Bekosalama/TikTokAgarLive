package com.tiktokagarlive.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

private data class Cell(
    var x: Float, var y: Float, var mass: Float, val color: Int,
    val name: String, var vx: Float = 0f, var vy: Float = 0f,
    var alive: Boolean = true
) {
    val radius get() = sqrt(mass) * 2.25f
}

private data class Pellet(val x: Float, val y: Float, val color: Int)

class ArenaView(context: Context) : View(context) {
    private val random = Random(56)
    private val worldW = 3600f
    private val worldH = 2400f
    private val bots = mutableListOf<Cell>()
    private val pellets = mutableListOf<Pellet>()
    private val viruses = mutableListOf<PointF>()
    private val names = listOf("Beko", "Maryam", "Mustafa", "Luna", "Ninja", "King", "Salem", "Rose", "M7md", "Queen")
    private val colors = intArrayOf(
        Color.rgb(255, 82, 120), Color.rgb(70, 190, 255), Color.rgb(255, 190, 65),
        Color.rgb(150, 105, 255), Color.rgb(65, 220, 160), Color.rgb(255, 115, 65),
        Color.rgb(245, 100, 210), Color.rgb(180, 220, 75)
    )
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD91B2234.toInt() }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x223E6688; strokeWidth = 1f }
    private val player = Cell(worldW / 2, worldH / 2, 650f, Color.rgb(40, 210, 160), "YOU")
    private var lastTime = System.nanoTime()
    private var score = 650
    private var eaten = 0
    private var cameraX = player.x
    private var cameraY = player.y
    private var targetX = player.x
    private var targetY = player.y
    private var dragging = false
    private var frame = 0

    init {
        isFocusable = true
        repeat(500) { i ->
            bots += Cell(
                random.nextFloat() * worldW, random.nextFloat() * worldH,
                random.nextFloat() * 170f + 35f, colors[random.nextInt(colors.size)],
                names[i % names.size] + (i + 1)
            )
        }
        repeat(520) { addPellet() }
        repeat(18) {
            viruses += PointF(random.nextFloat() * worldW, random.nextFloat() * worldH)
        }
    }

    private fun addPellet() {
        pellets += Pellet(random.nextFloat() * worldW, random.nextFloat() * worldH, colors[random.nextInt(colors.size)])
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0f, 0.04f)
        lastTime = now
        updateWorld(dt)
        drawWorld(canvas)
        postInvalidateOnAnimation()
    }

    private fun updateWorld(dt: Float) {
        frame++
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val scale = min(w / 1100f, h / 650f).coerceIn(0.55f, 1.35f)
        val viewW = w / scale
        val viewH = h / scale

        // AI movement: seek nearby food, avoid much larger cells, and occasionally chase smaller cells.
        bots.forEachIndexed { idx, b ->
            if (!b.alive) return@forEachIndexed
            var ax = 0f; var ay = 0f
            val nearestFood = pellets.minByOrNull { sq(it.x - b.x) + sq(it.y - b.y) }
            if (nearestFood != null) {
                val dx = nearestFood.x - b.x; val dy = nearestFood.y - b.y
                val d = hypot(dx, dy).coerceAtLeast(1f)
                ax += dx / d * 35f; ay += dy / d * 35f
            }
            val threat = bots.asSequence().filter { it !== b && it.alive && it.mass > b.mass * 1.55f }
                .minByOrNull { sq(it.x - b.x) + sq(it.y - b.y) }
            if (threat != null) {
                val dx = b.x - threat.x; val dy = b.y - threat.y
                val d = hypot(dx, dy).coerceAtLeast(1f)
                if (d < 330f) { ax += dx / d * 125f; ay += dy / d * 125f }
            }
            val angle = (idx * 2.399f) + frame * 0.003f
            ax += cos(angle) * 16f; ay += sin(angle) * 16f
            b.vx = (b.vx + ax * dt).coerceIn(-105f, 105f) * 0.985f
            b.vy = (b.vy + ay * dt).coerceIn(-105f, 105f) * 0.985f
            b.x = (b.x + b.vx * dt).coerceIn(20f, worldW - 20f)
            b.y = (b.y + b.vy * dt).coerceIn(20f, worldH - 20f)
        }

        // Bot-vs-food and bot-vs-bot eating.
        bots.forEach { b ->
            if (!b.alive) return@forEach
            val it = pellets.iterator()
            while (it.hasNext()) {
                val p = it.next()
                if (sq(p.x - b.x) + sq(p.y - b.y) < sq(b.radius + 7f)) {
                    b.mass += 2.2f; it.remove()
                }
            }
        }
        for (i in bots.indices) {
            val a = bots[i]
            if (!a.alive) continue
            for (j in i + 1 until bots.size) {
                val b = bots[j]
                if (!b.alive) continue
                val big = if (a.mass >= b.mass) a else b
                val small = if (big === a) b else a
                val d2 = sq(big.x - small.x) + sq(big.y - small.y)
                if (big.mass > small.mass * 1.28f && d2 < sq(big.radius * 0.78f)) {
                    big.mass += small.mass * 0.72f
                    small.alive = false
                }
            }
        }
        if (bots.count { !it.alive } > 80) {
            bots.removeAll { !it.alive }
            while (bots.size < 500) bots += Cell(random.nextFloat()*worldW, random.nextFloat()*worldH,
                random.nextFloat()*150f+35f, colors[random.nextInt(colors.size)], names[random.nextInt(names.size)] + random.nextInt(999))
        }
        while (pellets.size < 520) addPellet()

        // Player auto-pilot follows the nearest food while avoiding larger bots.
        var ax = 0f; var ay = 0f
        val food = pellets.minByOrNull { sq(it.x - player.x) + sq(it.y - player.y) }
        if (food != null) {
            val dx = food.x - player.x; val dy = food.y - player.y
            val d = hypot(dx, dy).coerceAtLeast(1f)
            ax += dx / d * 52f; ay += dy / d * 52f
        }
        val danger = bots.filter { it.alive && it.mass > player.mass * 1.45f }
            .minByOrNull { sq(it.x - player.x) + sq(it.y - player.y) }
        if (danger != null) {
            val dx = player.x - danger.x; val dy = player.y - danger.y
            val d = hypot(dx, dy).coerceAtLeast(1f)
            if (d < 420f) { ax += dx/d*180f; ay += dy/d*180f }
        }
        if (dragging) {
            val dx = targetX - player.x; val dy = targetY - player.y
            val d = hypot(dx, dy).coerceAtLeast(1f)
            ax += dx/d*130f; ay += dy/d*130f
        }
        player.vx = (player.vx + ax*dt).coerceIn(-150f,150f)*0.985f
        player.vy = (player.vy + ay*dt).coerceIn(-150f,150f)*0.985f
        player.x = (player.x + player.vx*dt).coerceIn(20f,worldW-20f)
        player.y = (player.y + player.vy*dt).coerceIn(20f,worldH-20f)

        val pit = pellets.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            if (sq(p.x-player.x)+sq(p.y-player.y) < sq(player.radius+8f)) {
                player.mass += 2.5f; score += 3; pit.remove()
            }
        }
        val bit = bots.iterator()
        while (bit.hasNext()) {
            val b = bit.next()
            if (!b.alive) continue
            val d2 = sq(b.x-player.x)+sq(b.y-player.y)
            if (player.mass > b.mass*1.28f && d2 < sq(player.radius*0.8f)) {
                player.mass += b.mass*0.72f; score += b.mass.toInt(); eaten++; b.alive = false
            } else if (b.mass > player.mass*1.28f && d2 < sq(b.radius*0.8f)) {
                player.mass = 650f; score = 650; eaten = 0
                player.x = worldW/2; player.y = worldH/2
                break
            }
        }
        cameraX += (player.x-cameraX)*0.055f
        cameraY += (player.y-cameraY)*0.055f
    }

    private fun drawWorld(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val scale = min(w / 1100f, h / 650f).coerceIn(0.55f, 1.35f)
        canvas.drawColor(Color.rgb(9, 16, 34))
        canvas.save()
        canvas.scale(scale, scale)
        val vw = w/scale; val vh = h/scale
        val left = cameraX-vw/2; val top = cameraY-vh/2
        canvas.translate(-left, -top)

        // Colorful arena background and subtle grid.
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(left, top, left+worldW, top+worldH,
                intArrayOf(Color.rgb(15,35,66), Color.rgb(24,38,78), Color.rgb(13,55,66)),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f,0f,worldW,worldH,bg)
        var gx = (left/100).toInt()*100f
        while (gx < left+vw+100) { canvas.drawLine(gx,top-100,gx,top+vh+100,gridPaint); gx+=100f }
        var gy = (top/100).toInt()*100f
        while (gy < top+vh+100) { canvas.drawLine(left-100,gy,left+vw+100,gy,gridPaint); gy+=100f }

        pellets.forEach { p ->
            cellPaint.color = p.color
            canvas.drawCircle(p.x,p.y,5.5f,cellPaint)
            cellPaint.color = Color.argb(90,255,255,255)
            canvas.drawCircle(p.x-1.5f,p.y-1.5f,1.8f,cellPaint)
        }
        viruses.forEach { v ->
            cellPaint.color = Color.rgb(52,235,116)
            drawSpiky(canvas,v.x,v.y,35f,cellPaint)
            cellPaint.color = Color.rgb(17,95,62)
            canvas.drawCircle(v.x,v.y,22f,cellPaint)
            textPaint.textSize = 15f
            canvas.drawText("✦",v.x,v.y+5f,textPaint)
        }
        bots.filter { it.alive }.sortedBy { it.mass }.forEach { drawCell(canvas,it,false) }
        drawCell(canvas,player,true)
        canvas.restore()

        // HUD overlays.
        drawPanel(canvas, 18f, 18f, 290f, 104f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE; textPaint.textSize = 25f
        canvas.drawText("TIKTOK AGAR LIVE", 36f, 52f, textPaint)
        textPaint.textSize = 17f; textPaint.color = Color.rgb(155,225,255)
        canvas.drawText("MASS  ${player.mass.toInt()}   •   EATEN  $eaten", 36f, 85f, textPaint)
        textPaint.color = Color.WHITE; textPaint.textSize = 16f
        canvas.drawText("500 BOT ARENA  •  AUTO MODE", 36f, 108f, textPaint)

        drawPanel(canvas, w-220f, 18f, 202f, 250f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 19f; textPaint.color = Color.WHITE
        canvas.drawText("TOP 10", w-119f, 49f, textPaint)
        val leaders = (bots.filter { it.alive } + player).sortedByDescending { it.mass }.take(10)
        textPaint.textAlign = Paint.Align.LEFT; textPaint.textSize = 14f
        leaders.forEachIndexed { i, c ->
            textPaint.color = if (c === player) Color.rgb(90,255,190) else Color.WHITE
            canvas.drawText("${i+1}. ${c.name.take(12)}", w-204f, 78f+i*18f, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(c.mass.toInt().toString(), w-34f, 78f+i*18f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }

        drawPanel(canvas, w/2-170f, h-62f, 340f, 44f)
        textPaint.textAlign = Paint.Align.CENTER; textPaint.textSize = 15f
        textPaint.color = Color.rgb(230,240,255)
        canvas.drawText("AUTO PILOT ON  •  TOUCH TO GUIDE", w/2, h-34f, textPaint)
    }

    private fun drawCell(canvas: Canvas, c: Cell, isPlayer: Boolean) {
        val r = c.radius.coerceIn(15f, 125f)
        cellPaint.color = c.color
        cellPaint.setShadowLayer(if(isPlayer) 18f else 7f,0f,3f, c.color)
        canvas.drawCircle(c.x,c.y,r,cellPaint)
        cellPaint.clearShadowLayer()
        cellPaint.style = Paint.Style.STROKE
        cellPaint.strokeWidth = if(isPlayer) 6f else 3f
        cellPaint.color = Color.argb(210,255,255,255)
        canvas.drawCircle(c.x,c.y,r,cellPaint)
        cellPaint.style = Paint.Style.FILL
        cellPaint.color = Color.argb(65,255,255,255)
        canvas.drawCircle(c.x-r*0.25f,c.y-r*0.3f,r*0.28f,cellPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.textSize = (r*0.34f).coerceIn(11f,25f)
        textPaint.setShadowLayer(3f,1f,2f,Color.BLACK)
        canvas.drawText(if(isPlayer) "YOU" else c.name.take(9),c.x,c.y+4f,textPaint)
        textPaint.textSize = (r*0.25f).coerceIn(9f,18f)
        canvas.drawText(c.mass.toInt().toString(),c.x,c.y+r*0.36f,textPaint)
        textPaint.clearShadowLayer()
    }

    private fun drawSpiky(canvas: Canvas, x: Float, y: Float, r: Float, paint: Paint) {
        val path = Path()
        for (i in 0 until 40) {
            val a = i * Math.PI.toFloat() / 20f
            val rr = if (i % 2 == 0) r else r*0.78f
            val px = x + cos(a)*rr; val py = y + sin(a)*rr
            if (i == 0) path.moveTo(px,py) else path.lineTo(px,py)
        }
        path.close(); canvas.drawPath(path,paint)
    }

    private fun drawPanel(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        canvas.drawRoundRect(x,y,x+w,y+h,18f,18f,hudPaint)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x6657D8C7; style = Paint.Style.STROKE; strokeWidth = 1.5f
        }
        canvas.drawRoundRect(x,y,x+w,y+h,18f,18f,p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scale = min(width.toFloat()/1100f, height.toFloat()/650f).coerceIn(0.55f,1.35f)
        when(event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val sx = event.x/scale - width/scale/2
                val sy = event.y/scale - height/scale/2
                targetX = (cameraX + sx).coerceIn(0f,worldW)
                targetY = (cameraY + sy).coerceIn(0f,worldH)
                dragging = true
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return true
    }

    private fun sq(v: Float) = v*v
}
