# SchoolHub – SKPU College Hukkeri (Stage 1 + Year selection, Notes, Live Classes)

Included: **First Year / Second Year selection before login**, **Notes**, **Live Class links**, login/registration (student, teacher, parent), admin approval, role security rules,
Digital Attendance Register (teacher), My Attendance (student). NOT yet included: marks, fees,
timetable, exams, e-learning, parent-child linking, multilingual, offline sync, push notifications.

## Step 1 – Firebase (free, no coding)
1. Go to https://console.firebase.google.com → Add project → name it SchoolHub.
2. Build → Authentication → Get started → enable **Email/Password**.
3. Build → Firestore Database → Create database → Production mode → choose region (Mumbai/asia-south1).
4. In Firestore → **Rules** tab → delete everything, paste the contents of `firestore.rules` → Publish.
5. Project settings (gear) → Add app → Android → package name: `com.skhukkeri.schoolhub` → Register →
   **Download google-services.json**.

## Step 2 – GitHub
1. Create a free account at github.com → New repository (e.g. schoolhub), Private.
2. Upload ALL files of this folder (drag and drop; keep the `.github` folder – if hidden, upload the ZIP's
   contents via "Add file → Upload files" from a computer).
3. Upload `google-services.json` **inside the `app` folder** (app/google-services.json).

## Step 3 – Get the APK
1. Repository → **Actions** tab → "Build APK" → wait ~5–8 minutes for green tick.
2. Open the finished run → scroll to **Artifacts** → download **SchoolHub-APK** → unzip → `app-debug.apk`.
3. Send to phone, allow "install unknown apps", install.
If the run is red, open it, copy the red error text and send it to Claude.

## Step 4 – Make yourself the Administrator
1. Install app, register as Student with your own details.
2. Firebase Console → Firestore → users → your document → change `role` to `admin` and `status` to `approved`.
3. Re-open app. You can now approve teachers and students.
(Anyone choosing "admin" in the app is impossible by design – rules block it.)

## New features (this version)
- Opening screen asks **First Year or Second Year**. A student registered in Year 1 cannot enter as Year 2.
  Teachers choose the year they are working in each time they log in.
- Student registration uses **Class/Stream** (e.g. SCIENCE, COMMERCE, ARTS) + Section. Teachers must type the same
  stream spelling when posting notes (SCIENCE = Science).
- **Notes tab**: teacher posts a title, subject, text and an optional link (Google Drive PDF, YouTube video).
  Students see only notes for their own year and stream.
- **Live Class tab**: teacher pastes a Google Meet / Zoom / YouTube Live link with date/time; students tap **Join**.
  (Create the meeting link in Google Meet first, then paste it. The app does not stream video itself.)
- **IMPORTANT:** re-publish `firestore.rules` in Firebase (Firestore → Rules) – it changed.
- **IMPORTANT:** accounts made with the previous version have no year. Delete them in Firebase
  (Authentication → Users, and Firestore → users) and register again.

## Known limits
- Direct file upload (PDF/video inside the app) needs Firebase Storage, which now requires a paid Blaze plan
  (small schools usually pay almost nothing). For now notes use links. Can be added later.
- Not compiled or tested by me – expect a small fix or two on first build.
- School ID photo upload/OCR, duplicate-ID blocking, attendance audit log, edit-reasons,
  absentee analytics, PDF export and parent notifications are planned for the next stages.
- Debug APK is fine for your school's own phones; a signed release APK is a later step (needed for Play Store).
