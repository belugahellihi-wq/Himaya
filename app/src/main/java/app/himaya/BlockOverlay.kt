package app.himaya

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Полноэкранное окно поверх всех приложений: аят/хадис + кнопка, которая оживает через 7 секунд. */
class BlockOverlay(private val ctx: Context) {
    private var root: View? = null
    private var timer: CountDownTimer? = null
    private val wm: WindowManager get() = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun pill(c: Int) = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(c) }
    private fun tv(t: String, sp: Float, c: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = t; textSize = sp; setTextColor(c); gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    fun show() {
        if (root != null) return
        val gold = Color.parseColor("#D4AF37")
        val v = Verses.random()

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(64), dp(28), dp(48))
        }
        col.addView(tv("Пауза", 40f, Color.parseColor("#E5484D"), true).apply { letterSpacing = 0.2f })
        col.addView(tv("Сделай вдох. Ты можешь выбрать другое действие.", 16f, Color.parseColor("#9FB8AE"))
            .apply { setPadding(0, dp(8), 0, dp(36)) })
        if (v.ar.isNotEmpty()) {
            col.addView(tv(v.ar, 27f, gold).apply {
                textDirection = View.TEXT_DIRECTION_RTL
                setPadding(0, 0, 0, dp(22))
            })
        }
        col.addView(tv(v.ru, 19f, Color.WHITE).apply { setPadding(0, 0, 0, dp(14)) })
        col.addView(tv(v.src, 14f, gold))

        var ready = false
        val btn = tv("", 16f, Color.WHITE, true).apply {
            setPadding(dp(28), dp(14), dp(28), dp(14))
            background = pill(Color.parseColor("#2A3A35"))
        }
        col.addView(btn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(40) })

        timer = object : CountDownTimer(7000, 1000) {
            override fun onTick(ms: Long) { btn.text = ctx.getString(R.string.pause_seconds, (ms + 500) / 1000) }
            override fun onFinish() {
                ready = true
                btn.text = "Я остановился · на главный экран"
                btn.background = pill(Color.parseColor("#14684A"))
            }
        }.start()

        btn.setOnClickListener {
            if (!ready) return@setOnClickListener
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) { }
            hide()
        }

        col.addView(Button(ctx).apply {
            text = "Закрыть напоминание"
            setOnClickListener { hide() }
        })

        val sv = ScrollView(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0F0D"))
            isFillViewport = true
            addView(col)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        )
        try { wm.addView(sv, lp); root = sv } catch (e: Exception) { timer?.cancel() }
    }

    fun hide() {
        timer?.cancel()
        root?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        root = null
    }
}
