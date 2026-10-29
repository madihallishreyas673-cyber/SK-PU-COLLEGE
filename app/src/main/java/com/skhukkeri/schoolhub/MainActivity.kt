package com.skhukkeri.schoolhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.time.LocalDate

val Navy = Color(0xFF0B1630)
val Gold = Color(0xFFD4AF37)
val Panel = Color(0xFF14213D)
val Green = Color(0xFF2E9E5B)
val Red = Color(0xFFC0392B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Gold, onPrimary = Color.Black, secondary = Gold,
                    background = Navy, surface = Panel, onBackground = Color.White, onSurface = Color.White
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Navy) { AppRoot() }
            }
        }
    }
}

data class Profile(val uid: String, val name: String, val role: String, val status: String, val idNumber: String, val classKey: String, val year: Int = 0, val cls: String = "")
data class Student(val uid: String, val name: String, val roll: String)

val db get() = FirebaseFirestore.getInstance()
val auth get() = FirebaseAuth.getInstance()

fun clean(s: String) = s.trim().uppercase().replace(Regex("[^A-Z0-9-]"), "")

@Composable
fun YearGate(onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(130.dp))
        Text(stringResource(R.string.school_name), style = MaterialTheme.typography.titleLarge, color = Gold, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("Select your year to continue")
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onPick(1) }, Modifier.fillMaxWidth().height(60.dp)) { Text("First Year") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onPick(2) }, Modifier.fillMaxWidth().height(60.dp)) { Text("Second Year") }
    }
}

@Composable
fun AppRoot() {
    var year by rememberSaveable { mutableStateOf(0) }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(Unit) {
        val l = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(l)
        onDispose { auth.removeAuthStateListener(l) }
    }
    val id = uid
    if (year == 0) YearGate { year = it }
    else if (id == null) AuthScreen(year) { year = 0 }
    else Authed(id, year) { auth.signOut(); year = 0 }
}

@Composable
fun Authed(id: String, year: Int, logout: () -> Unit) {
    var profile by remember(id) { mutableStateOf<Profile?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    DisposableEffect(id) {
        val reg = db.collection("users").document(id).addSnapshotListener { s, _ ->
            profile = if (s != null && s.exists()) Profile(
                id, s.getString("name") ?: "", s.getString("role") ?: "", s.getString("status") ?: "",
                s.getString("idNumber") ?: "", s.getString("classKey") ?: "",
                (s.getLong("year") ?: 0L).toInt(), s.getString("cls") ?: ""
            ) else null
            loaded = true
        }
        onDispose { reg.remove() }
    }
    val p = profile
    when {
        !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        p == null -> Column(Modifier.padding(24.dp)) {
            Text("Profile not found. Please log out and register again.")
            Button(onClick = logout) { Text("Log out") }
        }
        p.status == "pending" -> StatusScreen(p, "Waiting for school approval. The administrator must approve your account.", logout)
        p.status != "approved" -> StatusScreen(p, "Your account was ${p.status}. Contact the school office.", logout)
        p.role == "student" && p.year != year ->
            StatusScreen(p, "You are registered in ${if (p.year == 1) "First" else "Second"} Year. Log out and choose the correct year.", logout)
        else -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.school_name), color = Gold, fontWeight = FontWeight.Bold)
                    Text("${p.name} • ${p.role.replaceFirstChar { it.uppercase() }} • ${if (year == 1) "First" else "Second"} Year", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = logout) { Text("Log out") }
            }
            when (p.role) {
                "teacher" -> Tabs(listOf("Attendance", "Notes", "Live Class")) {
                    when (it) { 0 -> TeacherScreen(p, year); 1 -> ContentScreen(p, year, false); else -> ContentScreen(p, year, true) }
                }
                "student" -> Tabs(listOf("Attendance", "Notes", "Live Class")) {
                    when (it) { 0 -> StudentScreen(p); 1 -> ContentScreen(p, year, false); else -> ContentScreen(p, year, true) }
                }
                "admin" -> AdminScreen()
                else -> Box(Modifier.padding(24.dp)) { Text("Parent portal (linking to your child) arrives in the next stage.") }
            }
        }
    }
}

@Composable
fun Tabs(labels: List<String>, content: @Composable (Int) -> Unit) {
    var t by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = t) {
            labels.forEachIndexed { i, l -> Tab(selected = t == i, onClick = { t = i }, text = { Text(l) }) }
        }
        Box(Modifier.weight(1f)) { content(t) }
    }
}

@Composable
fun StatusScreen(p: Profile, msg: String, logout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(100.dp))
        Spacer(Modifier.height(16.dp))
        Text("Hello ${p.name}", style = MaterialTheme.typography.titleLarge, color = Gold)
        Spacer(Modifier.height(8.dp))
        Text(msg)
        Spacer(Modifier.height(16.dp))
        Button(onClick = logout) { Text("Log out") }
    }
}

@Composable
fun AuthScreen(year: Int, changeYear: () -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var idNo by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf("") }
    var sec by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("student") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(110.dp))
        Text(stringResource(R.string.school_name), style = MaterialTheme.typography.titleLarge, color = Gold, fontWeight = FontWeight.Bold)
        Text((if (year == 1) "First Year" else "Second Year") + " • " + (if (register) "Create account" else "Login"), Modifier.padding(vertical = 8.dp))
        TextButton(onClick = changeYear) { Text("Change year") }
        if (register) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("student", "teacher", "parent").forEach {
                    FilterChip(selected = role == it, onClick = { role = it }, label = { Text(it.replaceFirstChar { c -> c.uppercase() }) })
                }
            }
            Field("Full name", name) { name = it }
            if (role != "parent") Field(if (role == "student") "Roll number" else "Employee ID", idNo) { idNo = it }
            if (role == "student") {
                Field("Class / Stream (e.g. SCIENCE)", cls) { cls = it }
                Field("Section (e.g. A)", sec) { sec = it }
            }
        }
        Field("Email", email, KeyboardType.Email) { email = it }
        Field("Password (min 6 characters)", pass, KeyboardType.Password, true) { pass = it }
        if (register) Field("Confirm password", pass2, KeyboardType.Password, true) { pass2 = it }
        if (msg.isNotEmpty()) Text(msg, color = Red, modifier = Modifier.padding(8.dp))
        Button(
            enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = {
                msg = ""
                if (email.isBlank() || pass.length < 6) { msg = "Enter email and a password of at least 6 characters."; return@Button }
                busy = true
                if (!register) {
                    auth.signInWithEmailAndPassword(email.trim(), pass).addOnCompleteListener {
                        busy = false
                        if (!it.isSuccessful) msg = it.exception?.localizedMessage ?: "Login failed"
                    }
                } else {
                    if (name.isBlank() || (role != "parent" && idNo.isBlank()) || (role == "student" && (cls.isBlank() || sec.isBlank())) || pass != pass2) {
                        busy = false; msg = "Fill all fields and make sure passwords match."; return@Button
                    }
                    auth.createUserWithEmailAndPassword(email.trim(), pass).addOnCompleteListener { t ->
                        if (!t.isSuccessful) { busy = false; msg = t.exception?.localizedMessage ?: "Failed"; return@addOnCompleteListener }
                        val u = t.result.user!!.uid
                        db.collection("users").document(u).set(
                            mapOf(
                                "name" to name.trim(), "role" to role, "status" to "pending",
                                "idNumber" to idNo.trim(), "email" to email.trim(),
                                "year" to year, "cls" to if (role == "student") clean(cls) else "",
                                "classKey" to if (role == "student") "Y$year-" + clean(cls) + "-" + clean(sec) else "",
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnCompleteListener { busy = false; if (!it.isSuccessful) msg = it.exception?.localizedMessage ?: "Save failed" }
                    }
                }
            }
        ) { Text(if (register) "Register" else "Login") }
        TextButton(onClick = { register = !register; msg = "" }) { Text(if (register) "Have an account? Login" else "New here? Register") }
        if (!register) TextButton(onClick = {
            if (email.isBlank()) msg = "Type your email first." else
                auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { msg = if (it.isSuccessful) "Reset link sent to your email." else (it.exception?.localizedMessage ?: "Failed") }
        }) { Text("Forgot password?") }
    }
}

@Composable
fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
fun TeacherScreen(p: Profile, year: Int) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var cls by remember { mutableStateOf("") }
    var sec by remember { mutableStateOf("") }
    var students by remember { mutableStateOf(listOf<Student>()) }
    val marks = remember { mutableStateMapOf<String, String>() }
    var q by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }

    fun load() {
        val k = "Y$year-" + clean(cls) + "-" + clean(sec)
        if (clean(cls).isEmpty() || clean(sec).isEmpty()) { status = "Enter class and section."; return }
        key = k; status = "Loading..."; marks.clear()
        db.collection("users").whereEqualTo("role", "student").whereEqualTo("status", "approved").whereEqualTo("classKey", k).get()
            .addOnSuccessListener { s ->
                students = s.documents.map { Student(it.id, it.getString("name") ?: "", it.getString("idNumber") ?: "") }
                    .sortedBy { it.roll.toIntOrNull() ?: Int.MAX_VALUE }
                db.collection("attendance").whereEqualTo("classKey", k).whereEqualTo("date", date.toString()).get()
                    .addOnSuccessListener { a ->
                        a.documents.forEach { d -> marks[d.getString("studentUid") ?: ""] = d.getString("status") ?: "" }
                        status = "${students.size} students loaded."
                    }.addOnFailureListener { status = it.localizedMessage ?: "Error" }
            }.addOnFailureListener { status = it.localizedMessage ?: "Error" }
    }

    fun save() {
        val toSave = students.filter { marks[it.uid] == "P" || marks[it.uid] == "A" }
        if (toSave.isEmpty()) { status = "Nothing marked yet."; return }
        val batch = db.batch()
        toSave.forEach { s ->
            batch.set(
                db.collection("attendance").document("${date}_${key}_${s.uid}"),
                mapOf("studentUid" to s.uid, "classKey" to key, "date" to date.toString(), "status" to marks[s.uid],
                    "markedBy" to p.uid, "updatedAt" to FieldValue.serverTimestamp())
            )
        }
        status = "Saving..."
        batch.commit().addOnSuccessListener { status = "Saved ✓ ${toSave.size} records for $date" }
            .addOnFailureListener { status = "NOT SAVED: ${it.localizedMessage}" }
    }

    val shown = students.filter { q.isBlank() || it.name.contains(q, true) || it.roll.contains(q, true) }
    val present = students.count { marks[it.uid] == "P" }
    val absent = students.count { marks[it.uid] == "A" }
    val unmarked = students.size - present - absent
    val pct = if (students.isEmpty()) 0 else (present + absent) * 100 / students.size

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text("Digital Attendance Register – ${if (year == 1) "First" else "Second"} Year", style = MaterialTheme.typography.titleMedium, color = Gold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cls, { cls = it }, label = { Text("Class/Stream") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(sec, { sec = it }, label = { Text("Section") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { date = date.minusDays(1); students = emptyList() }) { Text("◀ Prev") }
                Text(date.toString(), fontWeight = FontWeight.Bold)
                TextButton(onClick = { date = date.plusDays(1); students = emptyList() }) { Text("Next ▶") }
            }
            Button(onClick = { load() }, Modifier.fillMaxWidth()) { Text("Load register") }
            Text("P = Present   A = Absent   Not Marked = not recorded (not counted as absent)", style = MaterialTheme.typography.bodySmall)
            if (status.isNotEmpty()) Text(status, color = if (status.startsWith("NOT")) Red else Gold)
        }
        if (students.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Class $key • ${p.name}")
                        Text("Enrolled ${students.size} | Present $present | Absent $absent | Not marked $unmarked | Done $pct%")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { students.forEach { marks[it.uid] = "P" } }) { Text("Mark all present") }
                    OutlinedButton(onClick = { marks.clear() }) { Text("Clear") }
                }
                OutlinedTextField(q, { q = it }, label = { Text("Search roll no. or name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            items(shown, key = { it.uid }) { s ->
                val m = marks[s.uid]
                Row(Modifier.fillMaxWidth().background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.roll, Modifier.width(44.dp), fontWeight = FontWeight.Bold)
                    Text(s.name, Modifier.weight(1f))
                    Button(
                        onClick = { marks[s.uid] = "P" }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (m == "P") Green else Color.DarkGray, contentColor = Color.White)
                    ) { Text("P") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { marks[s.uid] = "A" }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (m == "A") Red else Color.DarkGray, contentColor = Color.White)
                    ) { Text("A") }
                }
            }
            item { Button(onClick = { save() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Review done – Save register") } }
        }
    }
}

@Composable
fun StudentScreen(p: Profile) {
    var rows by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var err by remember { mutableStateOf("") }
    LaunchedEffect(p.uid) {
        db.collection("attendance").whereEqualTo("studentUid", p.uid).get()
            .addOnSuccessListener { s -> rows = s.documents.map { (it.getString("date") ?: "") to (it.getString("status") ?: "") }.sortedByDescending { it.first } }
            .addOnFailureListener { err = it.localizedMessage ?: "Error" }
    }
    val pr = rows.count { it.second == "P" }
    val total = rows.size
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text("My Attendance", style = MaterialTheme.typography.titleMedium, color = Gold)
            Text("Roll ${p.idNumber} • Class ${p.classKey}")
            if (err.isNotEmpty()) Text(err, color = Red)
            Text(if (total == 0) "No attendance recorded yet." else "Present $pr of $total recorded sessions = ${"%.1f".format(pr * 100.0 / total)}%")
        }
        items(rows) { r ->
            Row(Modifier.fillMaxWidth().background(Panel).padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.first); Text(if (r.second == "P") "Present" else "Absent", color = if (r.second == "P") Green else Red)
            }
        }
    }
}

@Composable
fun AdminScreen() {
    var list by remember { mutableStateOf(listOf<Profile>()) }
    var msg by remember { mutableStateOf("") }
    fun refresh() {
        db.collection("users").whereEqualTo("status", "pending").get().addOnSuccessListener { s ->
            list = s.documents.map {
                Profile(it.id, it.getString("name") ?: "", it.getString("role") ?: "", "pending", it.getString("idNumber") ?: "", it.getString("classKey") ?: "", (it.getLong("year") ?: 0L).toInt())
            }
        }.addOnFailureListener { msg = it.localizedMessage ?: "Error" }
    }
    fun decide(id: String, st: String) {
        db.collection("users").document(id).update("status", st).addOnSuccessListener { refresh() }.addOnFailureListener { msg = it.localizedMessage ?: "Error" }
    }
    LaunchedEffect(Unit) { refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Pending approvals", style = MaterialTheme.typography.titleMedium, color = Gold)
            Text("Check the ID number against your school records before approving.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { refresh() }) { Text("Refresh") }
            if (msg.isNotEmpty()) Text(msg, color = Red)
            if (list.isEmpty()) Text("No pending requests.")
        }
        items(list, key = { it.uid }) { u ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(10.dp)) {
                    Text(u.name, fontWeight = FontWeight.Bold)
                    Text("${u.role} • Year ${u.year} • ID ${u.idNumber} ${if (u.classKey.isNotEmpty()) "• Class ${u.classKey}" else ""}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { decide(u.uid, "approved") }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White)) { Text("Approve") }
                        Button(onClick = { decide(u.uid, "rejected") }, colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)) { Text("Reject") }
                    }
                }
            }
        }
    }
}

data class Item(val id: String, val title: String, val subject: String, val text: String, val link: String,
                val cls: String, val by: String, val byName: String, val millis: Long, val whenText: String)

@Composable
fun ContentScreen(p: Profile, year: Int, live: Boolean) {
    val col = if (live) "liveClasses" else "notes"
    val teacher = p.role == "teacher"
    val uri = LocalUriHandler.current
    var items by remember { mutableStateOf(listOf<Item>()) }
    var msg by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf("") }
    var whenText by remember { mutableStateOf("") }

    fun refresh() {
        var q: Query = db.collection(col).whereEqualTo("year", year)
        if (!teacher) q = q.whereEqualTo("cls", p.cls)
        q.get().addOnSuccessListener { s ->
            items = s.documents.map {
                Item(it.id, it.getString("title") ?: "", it.getString("subject") ?: "", it.getString("text") ?: "",
                    it.getString("link") ?: "", it.getString("cls") ?: "", it.getString("createdBy") ?: "",
                    it.getString("teacherName") ?: "", it.getLong("createdMillis") ?: 0L, it.getString("whenText") ?: "")
            }.sortedByDescending { it.millis }
        }.addOnFailureListener { msg = it.localizedMessage ?: "Error" }
    }
    fun open(l: String) {
        try { uri.openUri(if (l.startsWith("http")) l else "https://$l") } catch (e: Exception) { msg = "Cannot open this link." }
    }
    fun post() {
        if (title.isBlank() || subject.isBlank() || clean(cls).isEmpty()) { msg = "Title, subject and class/stream are required."; return }
        if (live && (link.isBlank() || whenText.isBlank())) { msg = "Meeting link and date/time are required."; return }
        if (!live && text.isBlank() && link.isBlank()) { msg = "Write a note or add a link."; return }
        msg = "Posting..."
        db.collection(col).add(
            mapOf("year" to year, "cls" to clean(cls), "title" to title.trim(), "subject" to subject.trim(),
                "text" to text.trim(), "link" to link.trim(), "whenText" to whenText.trim(),
                "createdBy" to p.uid, "teacherName" to p.name, "createdMillis" to System.currentTimeMillis())
        ).addOnSuccessListener {
            msg = "Posted ✓"; title = ""; text = ""; link = ""; whenText = ""; showForm = false; refresh()
        }.addOnFailureListener { msg = "NOT POSTED: ${it.localizedMessage}" }
    }
    LaunchedEffect(year, live) { refresh() }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(if (live) "Live Classes" else "Notes & Study Material", style = MaterialTheme.typography.titleMedium, color = Gold)
            if (teacher) {
                Button(onClick = { showForm = !showForm }) { Text(if (showForm) "Close" else if (live) "＋ Schedule live class" else "＋ Add notes") }
                if (showForm) {
                    Field("Title", title) { title = it }
                    Field("Subject", subject) { subject = it }
                    Field("Class / Stream (e.g. SCIENCE)", cls) { cls = it }
                    if (live) Field("Date & time (e.g. 12 Nov, 10:00 AM)", whenText) { whenText = it }
                    if (!live) OutlinedTextField(text, { text = it }, label = { Text("Notes text") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), minLines = 3)
                    Field(if (live) "Meeting link (Google Meet / Zoom / YouTube Live)" else "Link to PDF / video (optional)", link, KeyboardType.Uri) { link = it }
                    Button(onClick = { post() }, Modifier.fillMaxWidth()) { Text("Post") }
                }
            }
            OutlinedButton(onClick = { refresh() }) { Text("Refresh") }
            if (msg.isNotEmpty()) Text(msg, color = if (msg.startsWith("NOT")) Red else Gold)
            if (items.isEmpty()) Text("Nothing here yet.")
        }
        items(items, key = { it.id }) { it ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(10.dp)) {
                    Text(it.title, fontWeight = FontWeight.Bold)
                    Text("${it.subject} • ${it.cls} • ${it.byName}", style = MaterialTheme.typography.bodySmall)
                    if (live && it.whenText.isNotEmpty()) Text("When: ${it.whenText}", color = Gold)
                    if (it.text.isNotEmpty()) Text(it.text, Modifier.padding(top = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (it.link.isNotEmpty()) Button(onClick = { open(it.link) }) { Text(if (live) "Join" else "Open link") }
                        if (teacher && it.by == p.uid) OutlinedButton(onClick = {
                            db.collection(col).document(it.id).delete().addOnSuccessListener { refresh() }.addOnFailureListener { e -> msg = e.localizedMessage ?: "Error" }
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
