package app.himaya

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import java.time.LocalDate
import java.util.Locale

class MainActivity : Activity() {
    private val prefs get()=getSharedPreferences("himaya",MODE_PRIVATE)
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var pageHost:FrameLayout
    private lateinit var nav:LinearLayout
    private var page="home"
    private var lastRunning=false
    private var lastCount=-1
    private var lastDay=""
    private var starting=false
    private var startRequestedAt=0L
    private var status:TextView?=null
    private var detail:TextView?=null
    private var toggle:Button?=null
    private var totalText:TextView?=null
    private var todayText:TextView?=null
    private var sessionText:TextView?=null
    private var hero: BrandHero? = null
    private var graphDays = 7
    private var weekChart:WeekView?=null
    private var weekEmpty:TextView?=null
    private var activeDialog:AlertDialog?=null
    private val refresh=object:Runnable{override fun run(){renderState();handler.postDelayed(this,1000)}}
    private val motionRequested get() = prefs.getBoolean("motion_1_2_1", true)
    private val motion get() = motionRequested && ValueAnimator.areAnimatorsEnabled()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun label(value:String,size:Float=14f,color:Int=Palette.muted,bold:Boolean=false)=TextView(this).apply{
        text=value;textSize=size;setTextColor(color);typeface=Typeface.create(if(bold)"sans-serif-medium" else "sans-serif",Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(),1f);includeFontPadding=false
    }
    private fun shape(color:Int=Palette.surface,radius:Int=22,stroke:Boolean=true)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();if(stroke)setStroke(dp(1),Palette.line)}
    private fun LinearLayout.put(v:View,top:Int=12,height:Int=ViewGroup.LayoutParams.WRAP_CONTENT){addView(v,LinearLayout.LayoutParams(-1,if(height>=0)dp(height)else height).apply{topMargin=dp(top)})}
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    private fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun card()=column().apply{background=shape();setPadding(dp(18),dp(18),dp(18),dp(18))}
    private fun button(value:String,primary:Boolean=true,action:()->Unit)=Button(this).apply{
        text=value;isAllCaps=false;textSize=15f;minHeight=dp(56);typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        setTextColor(if(primary)Palette.bg else Palette.text)
        background=RippleDrawable(ColorStateList.valueOf(0x3322A16B),if(primary)GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Palette.mint,0xFF63D399.toInt())).apply{cornerRadius=dp(17).toFloat()}else shape(Palette.surface,17),null)
        setPadding(dp(15),dp(12),dp(15),dp(12));setOnClickListener{action()}
    }
    private fun chip(value:String,color:Int=Palette.mint)=label(value,11f,color,true).apply{background=shape(Palette.surface,20);setPadding(dp(11),dp(7),dp(11),dp(7));gravity=Gravity.CENTER}
    private fun title(root:LinearLayout,eyebrow:String,heading:String,sub:String){
        root.put(label(eyebrow,11f,Palette.gold,true).apply{letterSpacing=.16f},26)
        root.put(label(heading,30f,Palette.text,true),12);root.put(label(sub,14f),10)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getString("page") ?: "home"
        val shell = column().apply { setBackgroundColor(Palette.bg) }
        pageHost = FrameLayout(this)
        shell.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = row().apply {
            background = shape(Palette.surface, 24)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        shell.addView(nav, LinearLayout.LayoutParams(-1, dp(67)).apply {
            setMargins(dp(18), dp(8), dp(18), dp(12))
        })
        setContentView(shell)
        showPage(page, false)
    }
    override fun onSaveInstanceState(outState:Bundle){outState.putString("page",page);super.onSaveInstanceState(outState)}
    override fun onResume(){super.onResume();if(page=="settings")showPage(page,false);handler.removeCallbacks(refresh);handler.post(refresh)}
    override fun onPause(){handler.removeCallbacks(refresh);activeDialog?.dismiss();activeDialog=null;super.onPause()}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);activeDialog?.dismiss();super.onDestroy()}
    private fun showPage(name:String,animate:Boolean=true){
        page=name;status=null;detail=null;toggle=null;totalText=null;todayText=null;sessionText=null;hero=null;weekChart=null;weekEmpty=null;pageHost.removeAllViews()
        val root=column().apply{setPadding(dp(22),dp(18),dp(22),dp(20))};val header=row()
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_himaya)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(36), dp(36)))
        header.addView(label("Himaya",22f,Palette.text,true),LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(10)})
        header.addView(chip("حماية",Palette.gold));root.put(header,0)
        when(name){"activity"->activityPage(root);"settings"->settingsPage(root);else->homePage(root)}
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root)
        }
        val artwork = hero
        if (artwork != null) {
            scroll.setOnScrollChangeListener { _, _, y, _, _ ->
                val progress = (y / dp(240).toFloat()).coerceIn(0f, 1f)
                artwork.setScrollProgress(if (motion) progress else 0f)
            }
        }
        scroll.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                val gutter = maxOf(dp(22), (right - left - dp(620)) / 2)
                root.setPadding(gutter, dp(18), gutter, dp(20))
            }
        }
        pageHost.addView(scroll)
        if(animate&&motion){root.alpha=0f;root.translationY=dp(12).toFloat();root.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()}
        nav.removeAllViews()
        listOf(Triple("home","shield","Главная"),Triple("activity","chart","Активность"),Triple("settings","settings","Настройки")).forEach{(id,icon,text)->
            val selected=id==name
            val item=column().apply{gravity=Gravity.CENTER;isClickable=true;isFocusable=true;isSelected=selected;contentDescription=text;setOnClickListener{if(page!=id)showPage(id)};if(selected)background=shape(0xFF234333.toInt(),18,false)}
            item.addView(IconView(this,icon,if(selected)Palette.mint else Palette.muted),LinearLayout.LayoutParams(dp(23),dp(23)))
            item.addView(label(text,11f,if(selected)Palette.mint else Palette.muted,selected).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4)})
            nav.addView(item,LinearLayout.LayoutParams(0,-1,1f))
        }
        lastCount=prefs.getInt("count",0);lastDay=LocalDate.now().toString();renderState()
    }
    private fun homePage(root:LinearLayout){
        val heroCard = card().apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF163B30.toInt(), Palette.surface, Palette.bg)
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Palette.line)
            }
            clipToOutline = true
        }
        hero = BrandHero(this)
        heroCard.put(hero!!, 0, 216)
        status = label("", 22f, Palette.text, true).apply { gravity = Gravity.CENTER }
        detail = label("", 13f).apply { gravity = Gravity.CENTER }
        heroCard.put(status!!, 0)
        heroCard.put(detail!!, 8)
        toggle = button("Включить фильтр") { toggleFilter() }
        heroCard.put(toggle!!, 20)
        root.put(heroCard, 18)
        val metrics=row()
        fun metric(icon:String,title:String):Pair<LinearLayout,TextView>{val box=card();val line=row();line.addView(IconView(this,icon),LinearLayout.LayoutParams(dp(18),dp(18)));line.addView(label(title,11f),LinearLayout.LayoutParams(-2,-2).apply{leftMargin=dp(8)});box.put(line,0);val value=label("0",22f,Palette.text,true);box.put(value,12);return box to value}
        val today=metric("shield","За сегодня");todayText=today.second;val session=metric("clock","Этот сеанс");sessionText=session.second
        metrics.addView(today.first,LinearLayout.LayoutParams(0,-1,1f).apply{rightMargin=dp(5)});metrics.addView(session.first,LinearLayout.LayoutParams(0,-1,1f).apply{leftMargin=dp(5)})
        root.put(metrics,24);root.put(label("Сегодня: заблокированные DNS-запросы.",11f),8)
        val pause=card();pause.put(label("Вернись в момент",18f,Palette.text,true),0);pause.put(label("30 секунд, чтобы выдохнуть и переключиться.",13f),8);pause.put(button("Сделать паузу",false){openPause()},15);root.put(pause,18)
        root.put(actionRow("plus","Мой список","Сайты, от которых хочешь отдохнуть"){openDomains()},14)
    }
    private fun activityPage(root:LinearLayout){
        title(root,"НА ТВОЕЙ СТОРОНЕ","Твоя активность","Только локальные цифры. Без истории посещений.")
        val stats=card();stats.put(label("ВСЕГО ЗАБЛОКИРОВАНО",10f,Palette.muted,true).apply{letterSpacing=.12f},0)
        totalText=label(prefs.getInt("count",0).toString(),46f,Palette.mint,true);stats.put(totalText!!,12);stats.put(label("DNS-запросов",13f),4);root.put(stats,24)
        val week = card()
        val period = row()
        for (length in listOf(7, 30)) {
            val option = button("$length дней", length == graphDays) {
                if (graphDays != length) {
                    graphDays = length
                    showPage("activity", false)
                }
            }
            option.isSelected = length == graphDays
            period.addView(option, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (length == 7) rightMargin = dp(8)
            })
        }
        week.put(period, 0)
        val days = dailyCounts(graphDays)
        weekChart=WeekView(this,days);week.put(weekChart!!,22,180)
        weekEmpty=label("Пока нет блокировок. График заполнится, когда фильтр начнёт отклонять запросы.",13f).apply{visibility=if(days.all{it.second==0})View.VISIBLE else View.GONE};week.put(weekEmpty!!,15)
        root.put(week,14)
        root.put(button("Поделиться отчётом", false) { shareReport() }, 12)
        root.put(label("В счётчик входят повторы и фоновые запросы. Это не число сайтов или твоих действий. Данные по дням собираются с этой версии.",13f),18)
        root.put(actionRow("lock","Данные остаются у тебя","На сервер Himaya статистика не отправляется"){privacy()},18)
        root.put(button("Сбросить статистику",false){AlertDialog.Builder(this).setTitle("Начать счёт заново?").setMessage("Общий счётчик и данные по дням будут удалены с этого устройства.").setNegativeButton("Отмена",null).setPositiveButton("Сбросить"){_,_->val e=prefs.edit().putInt("count",0);prefs.all.keys.filter{it.startsWith("day_")}.forEach{e.remove(it)};e.apply();showPage("activity",false)}.show()},20)
    }
    private fun dailyCounts(length: Int): List<Pair<LocalDate, Int>> {
        val today = LocalDate.now()
        return (length - 1 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            day to prefs.getInt("day_$day", 0)
        }
    }

    private fun shareReport() {
        val report = buildString {
            appendLine("Himaya · заблокированные DNS-запросы за $graphDays дней")
            for ((date, count) in dailyCounts(graphDays)) appendLine("$date — $count")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
        }
        openSettings(Intent.createChooser(intent, "Поделиться отчётом"))
    }

    private fun settingsPage(root:LinearLayout){
        title(root,"ТВОЙ РИТМ","Настрой под себя","Нужные функции. Никакого лишнего контроля.")
        val controls=card();controls.put(label("Ощущения",18f,Palette.text,true),0)
        controls.put(switchRow("Живой интерфейс","Параллакс при прокрутке и плавные переходы",motionRequested){prefs.edit().putBoolean("motion_1_2_1",it).apply()},16)
        controls.put(switchRow("Напоминания с аятами","Необязательное окно при блокировке",prefs.getBoolean("reminders",false)){checked->prefs.edit().putBoolean("reminders",checked).apply();if(checked&&!Settings.canDrawOverlays(this))openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))},18)
        if(prefs.getBoolean("reminders",false)&&!Settings.canDrawOverlays(this))controls.put(button("Разрешить напоминания",false){openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))},12)
        root.put(controls,24);root.put(actionRow("plus","Свой список сайтов","${domains().size} из ${DomainRules.LIMIT} доменов"){openDomains()},16)
        root.put(actionRow("shield","Настройки VPN","Разрешения и постоянная VPN"){openSettings(Intent(Settings.ACTION_VPN_SETTINGS))},12)
        root.put(actionRow("lock","Приватность и ограничения","Что фильтруется и какие данные передаются"){privacy()},12)
        val about=card();about.put(label("Для твоего спокойствия",17f,Palette.gold,true),0);about.put(label("Без аккаунта и рекламы. Ты можешь выключить фильтр, изменить свой список или закрыть напоминание в любой момент.",13f),10);about.put(label("Himaya 1.3 · предварительная версия",11f),18);root.put(about,22)
    }
    private fun actionRow(icon:String,title:String,sub:String,action:()->Unit):View{
        val r=row().apply{background=shape();setPadding(dp(16),dp(16),dp(13),dp(16));minimumHeight=dp(82);isClickable=true;isFocusable=true;contentDescription="$title. $sub";setOnClickListener{action()}}
        r.addView(IconView(this,icon),LinearLayout.LayoutParams(dp(24),dp(24)));val words=column();words.put(label(title,15f,Palette.text,true),0);words.put(label(sub,12f),6)
        r.addView(words,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(13);rightMargin=dp(9)});r.addView(IconView(this,"arrow",Palette.muted),LinearLayout.LayoutParams(dp(18),dp(18)));return r
    }
    private fun switchRow(title:String,sub:String,checked:Boolean,change:(Boolean)->Unit):View{
        val r=row();val words=column();words.put(label(title,15f,Palette.text,true),0);words.put(label(sub,12f),5);r.addView(words,LinearLayout.LayoutParams(0,-2,1f).apply{rightMargin=dp(8)})
        r.addView(Switch(this).apply{isChecked=checked;contentDescription=title;minHeight=dp(48);thumbTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(Palette.mint,Palette.muted));setOnCheckedChangeListener{_,value->change(value)}},LinearLayout.LayoutParams(-2,dp(48)));return r
    }
    private fun renderState(){
        val running=DnsVpnService.isRunning
        if(starting&&(running||SystemClock.elapsedRealtime()-startRequestedAt>5000||DnsVpnService.lastError!=null))starting=false
        status?.text=when{starting->"Включаем фильтр…";running&&DnsVpnService.lastError!=null->"Проверь соединение";running->"Фильтр включён";else->"Фильтр выключен"}
        status?.setTextColor(if(running&&DnsVpnService.lastError!=null)Palette.gold else Palette.mint)
        detail?.text=DnsVpnService.lastError?:if(running)"Фильтруем системные DNS-запросы" else "Включи защиту системных DNS-запросов"
        toggle?.text=when{starting->"Подключение…";running->"Выключить фильтр";else->"Включить фильтр"};toggle?.isEnabled=!starting;hero?.enabledFilter=running
        val today=LocalDate.now().toString();todayText?.text=prefs.getInt("day_$today",0).toString()
        val sec=if(running&&DnsVpnService.startedAt>0)(SystemClock.elapsedRealtime()-DnsVpnService.startedAt)/1000 else 0L
        sessionText?.text=String.format(Locale.ROOT,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60)
        val count=prefs.getInt("count",0);totalText?.text=count.toString()
        if(page=="activity"&&(lastCount!=count||lastDay!=today)){val days=dailyCounts(graphDays);weekChart?.days=days;weekEmpty?.visibility=if(days.all{it.second==0})View.VISIBLE else View.GONE};lastCount=count;lastDay=today
        if(running!=lastRunning&&motion)status?.animate()?.alpha(.4f)?.setDuration(80)?.withEndAction{status?.animate()?.alpha(1f)?.setDuration(180)?.start()}?.start();lastRunning=running
    }
    private fun toggleFilter(){
        if(DnsVpnService.isRunning)AlertDialog.Builder(this).setTitle("Выключить фильтр?").setMessage("Himaya перестанет фильтровать системные DNS-запросы.").setNegativeButton("Оставить",null).setPositiveButton("Выключить"){_,_->stopService(Intent(this,DnsVpnService::class.java));renderState()}.show()
        else AlertDialog.Builder(this).setTitle("Включим Himaya?").setMessage("Локальный VPN направляет DNS-запросы в Cloudflare Families по TLS. Cloudflare видит домены и IP. Другой VPN будет отключён.\n\nIP не скрывается. Сторонний защищённый DNS и прямые IP могут обходить фильтр.").setNegativeButton("Не сейчас",null).setPositiveButton("Продолжить"){_,_->val intent=VpnService.prepare(this);if(intent!=null)startActivityForResult(intent,1)else startVpn()}.show()
    }
    private fun startVpn(){try{DnsVpnService.lastError=null;starting=true;startRequestedAt=SystemClock.elapsedRealtime();startForegroundService(Intent(this,DnsVpnService::class.java));renderState();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2)}catch(_:Exception){starting=false;Toast.makeText(this,"Не удалось включить VPN",Toast.LENGTH_LONG).show();renderState()}}
    private fun openPause(){activeDialog=AlertDialog.Builder(this).setTitle("Просто выдохни").setView(PauseView(this,motion).apply{minimumHeight=dp(270)}).setNegativeButton("Вернуться",null).create();activeDialog?.show()}
    private fun domains()=prefs.getStringSet("custom_domains",emptySet())?.toSet()?:emptySet()
    private fun openDomains(){
        val content=column().apply{setPadding(dp(22),dp(6),dp(22),dp(12))};content.put(label("Домен и его поддомены блокируются при включённом фильтре. Изменения действуют на новые DNS-запросы; кэш браузера может задержать эффект.",13f),0)
        val saved=domains().sorted();if(saved.isEmpty())content.put(label("Твой список пока пуст",17f,Palette.text,true),24)
        saved.forEach{domain->val r=row();r.addView(label(domain,14f,Palette.text),LinearLayout.LayoutParams(0,-2,1f));r.addView(button("Убрать",false){prefs.edit().putStringSet("custom_domains",domains()-domain).apply();activeDialog?.dismiss();openDomains()},LinearLayout.LayoutParams(-2,-2).apply{leftMargin=dp(6)});content.put(r,10)}
        activeDialog=AlertDialog.Builder(this).setTitle("Мой список · ${saved.size}/${DomainRules.LIMIT}").setView(ScrollView(this).apply{addView(content)}).setNegativeButton("Готово"){_,_->if(page=="settings")showPage(page,false)}.setPositiveButton("Добавить"){_,_->addDomain()}.create();activeDialog?.show()
    }
    private fun addDomain(){
        val box=column().apply{setPadding(dp(22),dp(6),dp(22),dp(5))};val input=EditText(this).apply{hint="example.com";setTextColor(Palette.text);setHintTextColor(Palette.muted);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI;setSingleLine(true);filters=arrayOf(android.text.InputFilter.LengthFilter(2048))}
        box.put(input,0);box.put(label("Можно вставить адрес сайта. Укажи домен, который хочешь заблокировать.",12f),10)
        val dialog=AlertDialog.Builder(this).setTitle("Добавить сайт").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create();activeDialog=dialog;dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{val domain=DomainRules.normalize(input.text.toString());when{domain==null->input.error="Введи домен, например example.com";domain in domains()->input.error="Этот домен уже добавлен";domains().size>=DomainRules.LIMIT->input.error="В списке уже ${DomainRules.LIMIT} доменов";else->{prefs.edit().putStringSet("custom_domains",domains()+domain).apply();dialog.dismiss();openDomains()}}}
    }
    private fun privacy(){AlertDialog.Builder(this).setTitle("Без скрытых обещаний").setMessage("Himaya фильтрует системный DNS. IP не скрывается, весь трафик не шифруется. DNS передаётся Cloudflare Families по TLS; Cloudflare видит домены и IP.\n\nСторонний DoH/DoT, Private DNS, другой VPN, прямые IP и кэш могут обходить фильтр. Контент внутри разрешённых сайтов отдельно не фильтруется.\n\nНа устройстве хранятся настройки, твой список доменов, общий счётчик и дневные суммы. Истории посещений и аналитики нет.\n\nДля ожидаемой работы отключи сторонний Private DNS и Secure DNS браузера. Не включай «Блокировать соединения без VPN»: DNS-only режим с ним несовместим.").setPositiveButton("Понятно",null).show()}
    private fun openSettings(intent:Intent){try{startActivity(intent)}catch(_:Exception){Toast.makeText(this,"Открой настройки Android вручную",Toast.LENGTH_LONG).show()}}
    @Deprecated("Legacy activity result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==1&&resultCode==RESULT_OK)startVpn()}
}
