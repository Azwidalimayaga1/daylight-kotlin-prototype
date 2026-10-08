package com.daylight.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.text.InputType
import android.widget.*
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors

class ApiFailure(val status: Int, override val message: String) : Exception(message)

class MainActivity : Activity() {
    companion object {
        const val EMAIL = 101; const val PASSWORD = 102; const val NAME = 103; const val SUBMIT = 104
        const val AUTH_TOGGLE = 105; const val SETTINGS = 106; const val SAVE = 107; const val LOGOUT = 108
        const val DARK = 109; const val GOAL = 110; const val NUDGES = 111; const val STATUS = 112
        const val HOME = 113; const val ADD = 114; const val HABIT_FIRST = 115
    }
    private lateinit var session: SessionStore
    private val executor = Executors.newSingleThreadExecutor()
    private var token: String? = null
    private var user = JSONObject()
    private var habits = JSONArray()
    private var register = false
    private var screen = "auth"
    private var busy = false
    private var dark = false
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val preferences by lazy { getSharedPreferences("preferences", MODE_PRIVATE) }
    private val baseUrl get() = preferences.getString("api", "http://127.0.0.1:8080")!!
    private val ink get() = Color.parseColor(if (dark) "#EEF3E9" else "#20382E")
    private val muted get() = Color.parseColor(if (dark) "#A8B8AE" else "#6B7C71")
    private val paper get() = Color.parseColor(if (dark) "#14251E" else "#F6F5EF")
    private val cardColor get() = Color.parseColor(if (dark) "#20372B" else "#FFFFFF")
    private val green = Color.parseColor("#337D64")
    private val accent get() = Color.parseColor(if (dark) "#A2D8BA" else "#337D64")
    private val yellow = Color.parseColor("#F3D482")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionStore(this); token = session.read()
        auth()
        if (token != null) task("Restoring your session…") { user = request("GET", "/me"); habits = request("GET", "/habits").getJSONArray("habits"); runOnUiThread { home() } }
    }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun background(color: Int, radius: Int = 20) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Float = 16f, color: Int = ink, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun space(height: Int = 16) { content.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun add(view: View, parent: LinearLayout = content, bottom: Int = 12) {
        parent.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottom) })
    }
    private fun shell() {
        busy = false
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(paper) }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val sys = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(0, sys.top, 0, sys.bottom)
            } else { v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom) }
            insets
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(18), dp(24), dp(20)) }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1,-1)); setContentView(root)
        window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        add(text("◒  daylight", 23f, ink, true)); space(12)
        status = text("", 14f, muted).apply { id = STATUS; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    }
    private fun button(label: String, identifier: Int = View.NO_ID, primary: Boolean = true, action: () -> Unit): Button = Button(this).apply {
        id = identifier; text = label; isAllCaps = false; textSize = 16f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) Color.WHITE else ink); background = background(if (primary) green else cardColor, 16)
        minHeight = dp(54); setPadding(dp(16), dp(8), dp(16), dp(8)); setOnClickListener { if (!busy) action() }
    }
    private fun input(label: String, identifier: Int, value: String = "", kind: Int = InputType.TYPE_CLASS_TEXT): EditText {
        add(text(label, 13f, muted, true), bottom = 6)
        return EditText(this).apply {
            id = identifier; setText(value); hint = label; inputType = kind; setSingleLine(); textSize = 16f
            if (kind and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            }
            setTextColor(ink); setHintTextColor(muted); background = background(cardColor, 14)
            setPadding(dp(16), dp(14), dp(16), dp(14)); minHeight = dp(54)
        }.also { add(it, bottom = 16) }
    }
    private fun auth() {
        screen = "auth"; dark = false; shell()
        add(text("SMALL STEPS. BRIGHTER DAYS.", 11f, green, true)); space(2)
        add(text(if (register) "Start something\ngood." else "A little better,\nevery day.", 38f, ink, true), bottom = 10)
        add(text(if (register) "Create your account. Make room for the habits that matter." else "Welcome back. Your next small win is waiting.", 16f, muted), bottom = 24)
        val name = if (register) input("Your name", NAME) else null
        val email = input("Email address", EMAIL, kind = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = input("Password", PASSWORD, kind = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        add(text("Use at least 8 characters. Your password is never stored on this phone.", 12f, muted), bottom = 18)
        add(button(if (register) "Create account  →" else "Sign in  →", SUBMIT) {
            val pass = password.text.toString()
            if (pass.length !in 8..128) { notice("Use a password of 8–128 characters.", true); return@button }
            val payload = JSONObject().put("email",email.text.toString()).put("password",pass)
            if (register) payload.put("displayName",name!!.text.toString())
            task(if (register) "Creating your account…" else "Signing in…") {
                val result = request("POST",if (register) "/auth/register" else "/auth/login",payload)
                val newToken = result.getString("token"); session.save(newToken); token = newToken; user = result.getJSONObject("user")
                password.post { password.text.clear() }
                habits = request("GET", "/habits").getJSONArray("habits")
                runOnUiThread { home(); notice("Account connected. Your habits are synced.") }
            }
        })
        add(button(if (register) "Already a member? Sign in" else "New here? Create an account", AUTH_TOGGLE, false) { register = !register; auth() })
        add(status); space(10)
        add(text("YOUR ROUTINE, CONNECTED", 11f, green, true), bottom = 6)
        add(text("A personal space for everyday progress.", 13f, muted), bottom = 6)
        add(button("API connection", primary = false) { endpointDialog() })
    }
    private fun home() {
        screen = "home"; dark = user.optBoolean("darkMode"); shell()
        add(text("YOUR EVERYDAY SPACE", 11f, accent, true), bottom = 8)
        add(text("Hello, ${user.optString("displayName")}.", 30f, ink, true), bottom = 8)
        add(text("Progress starts with one small step.", 15f, muted), bottom = 22)
        val count = (0 until habits.length()).count { habits.getJSONObject(it).getBoolean("completed") }
        val goal = user.optInt("dailyGoal",3)
        val summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = background(green); setPadding(dp(22),dp(20),dp(22),dp(20)) }
        add(text("TODAY'S INTENTION",11f,yellow,true),summary,8)
        add(text("$count / $goal small wins",28f,Color.WHITE,true),summary,12)
        val progress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max = goal; progress = count.coerceAtMost(goal); progressTintList = ColorStateList.valueOf(yellow); progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#57957F")) }
        add(progress,summary,12)
        add(text(if (count >= goal) "You reached your goal. Keep that feeling." else "You have ${goal-count} more wins to reach your goal.",13f,Color.WHITE),summary,0)
        add(summary,bottom = 24)
        add(text("Your habits",22f,ink,true),bottom=4)
        add(text("Tap a habit to track your progress.",13f,muted),bottom=16)
        for (i in 0 until habits.length()) {
            val habit = habits.getJSONObject(i); val done = habit.getBoolean("completed")
            val row = button("${if (done) "✓" else "○"}   ${habit.getString("title")}", if(i==0) HABIT_FIRST else View.NO_ID, false) {
                task("Saving your progress…") {
                    request("PUT","/habits/${habit.getString("id")}",JSONObject().put("completed",!done))
                    habits = request("GET","/habits").getJSONArray("habits")
                    runOnUiThread { home(); notice("Progress saved to your account.") }
                }
            }
            row.gravity = Gravity.START or Gravity.CENTER_VERTICAL; row.setTextColor(if(done) accent else ink); add(row)
        }
        add(button("+  Add a habit", ADD, false) { addHabit() },bottom=18)
        if (user.optBoolean("reminders")) add(text("A gentle nudge: take a minute for yourself today.",13f,muted),bottom=18)
        add(button("Settings",SETTINGS) { settings() }); add(status)
        add(text("Synced with your REST API · SQLite",11f,muted),bottom=0)
    }
    private fun settings() {
        screen = "settings"; dark = user.optBoolean("darkMode"); shell()
        add(text("MAKE IT YOURS",11f,accent,true)); add(text("Your settings",32f,ink,true),bottom=8)
        add(text("Your preferences follow your account.",15f,muted),bottom=24)
        val name = input("Display name", NAME, user.getString("displayName"))
        val goal = input("Daily goal · 1 to 10 wins", GOAL, user.getInt("dailyGoal").toString(), InputType.TYPE_CLASS_NUMBER)
        fun toggle(label: String, description: String, identifier: Int, checked: Boolean): Switch {
            val s = Switch(this@MainActivity).apply { id=identifier; text=label; textSize=17f; setTextColor(ink); isChecked=checked; minHeight=dp(48); thumbTintList=ColorStateList.valueOf(green) }
            add(s,bottom=4); add(text(description,13f,muted),bottom=20); return s
        }
        val darkMode = toggle("Dark appearance","A calmer look for your everyday space.",DARK,user.getBoolean("darkMode"))
        val nudges = toggle("In-app nudges","Show a gentle reminder on your home screen.",NUDGES,user.getBoolean("reminders"))
        add(button("Save changes",SAVE) {
            val n = goal.text.toString().toIntOrNull()
            if (n==null || n !in 1..10) { notice("Set a goal between 1 and 10.",true); return@button }
            val payload = JSONObject().put("displayName",name.text.toString()).put("dailyGoal",n).put("darkMode",darkMode.isChecked).put("reminders",nudges.isChecked)
            task("Saving settings…") { user=request("PUT","/me/settings",payload); runOnUiThread { settings(); notice("Settings saved to your account.") } }
        })
        add(button("Back to my habits",HOME,false) { home() }); add(status)
        add(text("ACCOUNT",11f,muted,true),bottom=6); add(text(user.getString("email"),14f,ink),bottom=14)
        add(button("Sign out",LOGOUT,false) {
            task("Signing out…") { request("POST","/auth/logout",JSONObject()); session.clear(); token=null; user=JSONObject(); runOnUiThread { register=false; auth(); notice("Signed out securely.") } }
        })
    }
    private fun addHabit() {
        val field = EditText(this).apply { hint="e.g. Take a mindful walk"; setSingleLine(); setPadding(dp(24),dp(20),dp(24),dp(20)) }
        val dialog = AlertDialog.Builder(this).setTitle("One more small step").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Add",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title=field.text.toString().trim()
            if(title.length !in 1..80) { field.error="Use 1–80 characters"; return@setOnClickListener }
            dialog.dismiss(); task("Adding your habit…") { request("POST","/habits",JSONObject().put("title",title)); habits=request("GET","/habits").getJSONArray("habits"); runOnUiThread { home(); notice("New habit saved.") } }
        } }; dialog.show()
    }
    private fun endpointDialog() {
        val field = EditText(this).apply { setText(baseUrl); inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setPadding(dp(24),dp(12),dp(24),dp(12)) }
        val dialog=AlertDialog.Builder(this).setTitle("REST API connection").setMessage("USB phone: http://127.0.0.1:8080 after adb reverse. Emulator: http://10.0.2.2:8080. Remote servers require HTTPS.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = field.text.toString().trim().trimEnd('/')
            val valid = try { val uri=URI(url); uri.host!=null && uri.userInfo==null && uri.query==null && uri.fragment==null && (uri.path.isNullOrEmpty() || uri.path=="/") && (uri.scheme=="https" || (uri.scheme=="http" && uri.host in listOf("127.0.0.1","10.0.2.2","localhost"))) } catch (_: Exception) { false }
            if (!valid) { field.error="Use HTTPS, or a local development URL."; return@setOnClickListener }
            preferences.edit().putString("api",url).apply(); session.clear(); token=null; dialog.dismiss(); notice("API address saved.")
        } }; dialog.show()
    }
    private fun notice(message: String, error: Boolean = false) { status.text=message; status.setTextColor(if(error) Color.parseColor(if(dark) "#FFB4A9" else "#B23D35") else muted) }
    private fun task(message: String, work: () -> Unit) {
        if(busy) return
        busy=true; notice(message)
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(content.windowToken,0)
        executor.execute {
            try { work() } catch(e: Exception) {
                runOnUiThread {
                    if(e is ApiFailure && e.status==401 && token!=null) { session.clear(); token=null; register=false; auth() }
                    notice(e.message ?: "Something went wrong. Please try again.",true)
                }
            } finally { runOnUiThread { busy=false } }
        }
    }
    private fun request(method: String, path: String, data: JSONObject? = null): JSONObject {
        val connection = URI(baseUrl+path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method; connection.connectTimeout=8000; connection.readTimeout=15000
            connection.setRequestProperty("Accept","application/json")
            token?.let { connection.setRequestProperty("Authorization","Bearer $it") }
            if(data!=null) { connection.doOutput=true; connection.setRequestProperty("Content-Type","application/json"); connection.outputStream.use { it.write(data.toString().toByteArray()) } }
            val code=connection.responseCode
            val raw=(if(code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            val result=JSONObject(raw)
            if(code !in 200..299) throw ApiFailure(code,result.optString("error","Request failed ($code)."))
            return result
        } catch(e: ApiFailure) { throw e }
        catch(_: Exception) { throw Exception("Cannot reach your API. Start the server and check API connection.") }
        finally { connection.disconnect() }
    }
}
