package app.himaya

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import java.time.LocalDate
import kotlin.math.*

internal object Palette {
    val bg = Color.rgb(7,20,17)
    val surface = Color.rgb(15,34,28)
    val line = Color.rgb(35,63,51)
    val text = Color.rgb(238,247,240)
    val muted = Color.rgb(158,182,170)
    val mint = Color.rgb(157,240,194)
    val gold = Color.rgb(224,197,141)
}
internal fun View.sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

/** Brand artwork with separate orbit and logo layers. */
internal class BrandHero(context: Context) : android.widget.FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = RadialGradient(0f, 0f, 110f, 0x503FDC9D, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    private val logo = android.widget.ImageView(context).apply {
        setImageResource(R.drawable.ic_himaya)
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
    }
    private var orbitOffset = 0f
    var enabledFilter = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val side = (resources.displayMetrics.density * 126).toInt()
        addView(logo, LayoutParams(side, side, android.view.Gravity.CENTER))
    }

    fun setScrollProgress(progress: Float) {
        orbitOffset = progress * 52f
        logo.translationY = height * .15f * progress
        logo.scaleX = 1f - progress * .10f
        logo.scaleY = logo.scaleX
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / 300f, height / 232f)
        if (scale <= 0f) return
        canvas.save()
        canvas.translate(width / 2f, height / 2f + orbitOffset * scale)
        canvas.scale(scale, scale)
        paint.shader = glow
        paint.style = Paint.Style.FILL
        paint.alpha = if (enabledFilter) 255 else 150
        canvas.drawCircle(0f, 0f, 110f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Palette.line
        canvas.drawCircle(0f, 0f, 88f, paint)
        canvas.drawCircle(0f, 0f, 106f, paint)
        paint.style = Paint.Style.FILL
        for (i in 0..5) {
            val angle = i * PI.toFloat() / 3 + orbitOffset * .015f
            paint.color = if (i % 2 == 0) Palette.gold else Palette.mint
            canvas.drawCircle(cos(angle) * 106f, sin(angle) * 106f, if (i % 2 == 0) 2.5f else 1.4f, paint)
        }
        canvas.restore()
    }
}

internal class IconView @JvmOverloads constructor(context:Context,private val kind:String="shield",private val tint:Int=Palette.mint):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    override fun onDraw(c:Canvas) {
        val s=min(width,height)/24f;c.save();c.translate((width-24*s)/2,(height-24*s)/2);c.scale(s,s)
        p.color=tint;p.style=Paint.Style.STROKE;p.strokeWidth=1.7f;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND;path.reset()
        when(kind){
            "shield"->{path.moveTo(12f,3f);path.lineTo(20f,6f);path.lineTo(19f,14f);path.quadTo(17f,19f,12f,22f);path.quadTo(7f,19f,5f,14f);path.lineTo(4f,6f);path.close();c.drawPath(path,p)}
            "chart"->{c.drawLine(5f,19f,5f,13f,p);c.drawLine(12f,19f,12f,5f,p);c.drawLine(19f,19f,19f,9f,p)}
            "settings"->{for(i in 0..2){val y=6f+i*6;c.drawLine(4f,y,20f,y,p);p.style=Paint.Style.FILL;p.color=Palette.surface;c.drawCircle(if(i==1)15f else 9f,y,3f,p);p.style=Paint.Style.STROKE;p.color=tint;c.drawCircle(if(i==1)15f else 9f,y,3f,p)}}
            "plus"->{c.drawLine(12f,5f,12f,19f,p);c.drawLine(5f,12f,19f,12f,p)}
            "arrow"->{c.drawLine(6f,12f,18f,12f,p);path.moveTo(13f,7f);path.lineTo(18f,12f);path.lineTo(13f,17f);c.drawPath(path,p)}
            "clock"->{c.drawCircle(12f,12f,9f,p);path.moveTo(12f,7f);path.lineTo(12f,12f);path.lineTo(16f,14f);c.drawPath(path,p)}
            "lock"->{c.drawRoundRect(5f,10f,19f,21f,3f,3f,p);c.drawArc(8f,2f,16f,15f,180f,180f,false,p)}
        };c.restore()
    }
}
internal class WeekView @JvmOverloads constructor(context:Context,initialDays:List<Pair<LocalDate,Int>> = emptyList()):View(context){
    var days=initialDays
        set(value){field=value;contentDescription="Дневные блокировки: "+value.joinToString{"${it.first}: ${it.second} запросов"};invalidate()}
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val labels=listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")
    init{contentDescription="Дневные блокировки: "+days.joinToString{"${it.first}: ${it.second} запросов"}}
    override fun onDraw(c:Canvas){
        val d=resources.displayMetrics.density;val top=26*d;val bottom=height-27*d;val max=days.maxOfOrNull{it.second}?.coerceAtLeast(1)?:1;val cell = width / days.size.coerceAtLeast(1).toFloat()
        p.textAlign=Paint.Align.CENTER
        days.forEachIndexed{i,day->val x=cell*(i+.5f);val w=min(27*d,cell*.55f)
            p.color=0xFF1C392B.toInt();c.drawRoundRect(x-w/2,top,x+w/2,bottom,7*d,7*d,p)
            val bar=if(day.second==0)3*d else (bottom-top)*day.second/max.toFloat();p.color=if(i==6)Palette.mint else 0xFF438565.toInt();c.drawRoundRect(x-w/2,bottom-bar,x+w/2,bottom,7*d,7*d,p)
            if (days.size <= 7 || i % 7 == 0 || i == days.lastIndex) {
                p.textSize = sp(11f)
                p.color = Palette.muted
                val tick = if (days.size <= 7) labels[day.first.dayOfWeek.value - 1] else day.first.dayOfMonth.toString()
                c.drawText(tick, x, height - 5 * d, p)
            }
            if(day.second>0 && days.size <= 7){val value=day.second.toString();p.textSize=sp(11f);p.textSize*=min(1f,cell*.9f/p.measureText(value));p.color=Palette.text;c.drawText(value,x,top-9*d,p)}
        }
    }
}
internal class PauseView @JvmOverloads constructor(context:Context,private val allowMotion:Boolean = true):View(context){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private var elapsed=0f
    private val animator=ValueAnimator.ofFloat(0f,30f).apply{duration=30000;interpolator=LinearInterpolator();addUpdateListener{elapsed=it.animatedValue as Float;invalidate()}}
    init{contentDescription="Пауза на 30 секунд. Дыши в удобном темпе. Можно закрыть в любой момент."}
    override fun onAttachedToWindow(){super.onAttachedToWindow();if(allowMotion&&ValueAnimator.areAnimatorsEnabled())animator.start()}
    override fun onDetachedFromWindow(){animator.cancel();super.onDetachedFromWindow()}
    override fun onDraw(c:Canvas){val d=resources.displayMetrics.density;val x=width/2f;val y=height/2f-15*d;val cycle=elapsed%10;val amount=if(cycle<4)cycle/4 else 1-(cycle-4)/6;val radius=(62+amount*17)*d
        p.style=Paint.Style.FILL;p.color=0x222FE797;c.drawCircle(x,y,radius+15*d,p);p.style=Paint.Style.STROKE;p.strokeWidth=2*d;p.color=Palette.mint;c.drawCircle(x,y,radius,p);p.style=Paint.Style.FILL
        p.textAlign=Paint.Align.CENTER;p.textSize=sp(22f);p.color=Palette.text;c.drawText(if(!allowMotion||!ValueAnimator.areAnimatorsEnabled())"Твой темп" else if(elapsed>=30)"Готово" else if(cycle<4)"Вдох" else "Выдох",x,y+7*d,p)
        p.textSize=sp(13f);p.color=Palette.muted;c.drawText("Побудь здесь. Остальное подождёт.",x,height-18*d,p)
    }
}
