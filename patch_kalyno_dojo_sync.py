#!/usr/bin/env python3
from pathlib import Path
import subprocess, shutil, sys
from datetime import datetime

ROOT = Path.cwd()
MIGRATION_PATH = Path("supabase/migrations/20261127212628_create_student_dojo_attempts_sync.sql")
MIGRATION_SQL = """create table if not exists public.student_dojo_attempts (
  client_attempt_id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  flashcard_id text not null,
  attempted_at bigint not null,
  is_success boolean not null,
  attempt_number integer not null default 0,
  rating text not null,
  kind text not null,
  question text not null,
  student_answer text,
  reference_answer text not null,
  lesson_title text not null default '',
  subject_name text not null default '',
  is_legacy boolean not null default false,
  created_at timestamptz not null default now(),
  constraint student_dojo_attempts_attempt_number_nonnegative check (attempt_number >= 0)
);

create index if not exists student_dojo_attempts_user_attempted_idx
  on public.student_dojo_attempts (user_id, attempted_at desc, client_attempt_id);
create index if not exists student_dojo_attempts_user_card_idx
  on public.student_dojo_attempts (user_id, flashcard_id, attempted_at desc);

alter table public.student_dojo_attempts enable row level security;

drop policy if exists student_dojo_attempts_select_own on public.student_dojo_attempts;
create policy student_dojo_attempts_select_own
  on public.student_dojo_attempts for select to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists student_dojo_attempts_insert_own on public.student_dojo_attempts;
create policy student_dojo_attempts_insert_own
  on public.student_dojo_attempts for insert to authenticated
  with check ((select auth.uid()) = user_id);

grant select, insert on public.student_dojo_attempts to authenticated;
"""

FILES = [
    Path("app/src/main/java/ci/kalyno/app/data/cloud/CloudSync.kt"),
    Path("app/src/main/java/ci/kalyno/app/data/progress/DojoAttempts.kt"),
    Path("app/src/main/java/ci/kalyno/app/data/dashboard/DashboardDao.kt"),
    Path("app/src/main/java/ci/kalyno/app/ui/dojo/DojoViewModel.kt"),
]
def run(*args):
    return subprocess.run(args, cwd=ROOT, text=True, capture_output=True)
if not (ROOT / ".git").exists():
    sys.exit("Lance ce script depuis la racine de ~/KALYNO.")
if any(not (ROOT / p).is_file() for p in FILES):
    sys.exit("Un ou plusieurs fichiers attendus sont absents : vérifie la branche.")
if (ROOT / MIGRATION_PATH).exists():
    existing_migration = (ROOT / MIGRATION_PATH).read_text(encoding="utf-8")
    if existing_migration.strip() != MIGRATION_SQL.strip():
        sys.exit("Le fichier de migration existe déjà avec un contenu différent : arrêt de sécurité.")
status = run("git", "status", "--porcelain")
if status.returncode:
    sys.exit(status.stderr)
if status.stdout.strip():
    print(status.stdout)
    sys.exit("ARRÊT : modifications locales non commitées. Sauvegarde/commit-les avant le patch.")

backup = Path.home() / "kalyno-backups" / ("dojo-sync-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
backup.mkdir(parents=True, exist_ok=False)
for p in FILES:
    dest = backup / p
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / p, dest)
print("Sauvegarde :", backup)

def read(p): return (ROOT / p).read_text(encoding="utf-8")
def write(p, s): (ROOT / p).write_text(s, encoding="utf-8")
def once(s, old, new, label):
    n = s.count(old)
    if n != 1: raise RuntimeError(f"{label}: ancre attendue une fois, trouvée {n}.")
    return s.replace(old, new, 1)

# DAO des tentatives
p = FILES[1]; s = read(p)
if "allForStudent(studentId" not in s:
    s = once(s,
'''    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(attempt: DojoAttemptEntity): Long
''',
'''    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(attempt: DojoAttemptEntity): Long

    /** Import cloud idempotent : les doublons locaux sont ignorés par l'index unique. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(attempts: List<DojoAttemptEntity>)

    /** Toutes les tentatives de l'élève, triées de façon stable pour l'envoi cloud. */
    @Query("SELECT * FROM dojo_attempts WHERE studentId = :studentId ORDER BY attemptedAt ASC, id ASC")
    abstract suspend fun allForStudent(studentId: String): List<DojoAttemptEntity>
''', "DAO Dojo")
write(p, s)

# DAO de progression : renvoyer toutes les cartes réellement révisées.
p = FILES[2]; s = read(p)
if "allReviewedProgress(" not in s:
    s = once(s,
'''    suspend fun progressReviewedSince(studentId: String, since: Long, limit: Int): List<CardProgressEntity>

''',
'''    suspend fun progressReviewedSince(studentId: String, since: Long, limit: Int): List<CardProgressEntity>

    /** Rejeu complet idempotent : aucun élément ne saute si plusieurs cartes ont le même horodatage. */
    @Query(
        "SELECT * FROM card_progress WHERE studentId = :studentId AND lastReviewedAt IS NOT NULL " +
            "ORDER BY lastReviewedAt ASC, flashcardId ASC"
    )
    suspend fun allReviewedProgress(studentId: String): List<CardProgressEntity>

''', "DAO progression")
write(p, s)

# Synchronisation
p = FILES[0]; s = read(p)
if "pullDojoAttempts(" not in s:
    s = once(s,
        "import ci.kalyno.app.data.dashboard.KalynoDatabase\n",
        "import ci.kalyno.app.data.dashboard.KalynoDatabase\nimport ci.kalyno.app.data.progress.DojoAttemptEntity\n",
        "import DojoAttemptEntity")
    s = once(s,
        "    private val sessionDao = KalynoDatabase.get(appContext).sessionProgressDao()\n",
        "    private val sessionDao = KalynoDatabase.get(appContext).sessionProgressDao()\n    private val attemptDao = KalynoDatabase.get(appContext).attemptDao()\n",
        "DAO cloud")
    s = once(s,
'''        step("historique reçu") { pullLogs(studentId, userId, token) }
        step("historique envoyé") { pushLogs(studentId, userId, token) }

''',
'''        step("historique reçu") { pullLogs(studentId, userId, token) }
        step("historique envoyé") { pushLogs(studentId, userId, token) }
        step("tentatives Dojo reçues") { pullDojoAttempts(studentId, userId, token) }
        step("tentatives Dojo envoyées") { pushDojoAttempts(studentId, userId, token) }

''', "étapes Dojo")

    # Cartes : lecture par offset au lieu d'un curseur timestamp strict.
    s = once(s,
'''    private fun fetchCards(userId: String, since: Long, token: String): JSONArray {
        fun get(extended: Boolean) = JSONArray(
            SupabaseRest.request(
                "GET",
                "student_card_progress?user_id=eq.$userId&last_reviewed_at=gt.$since" +
                    "&select=${cardSelect(extended)}&order=last_reviewed_at.asc&limit=$PAGE_SIZE",
                token
            )
        )
''',
'''    private fun fetchCards(userId: String, offset: Int, token: String): JSONArray {
        fun get(extended: Boolean) = JSONArray(
            SupabaseRest.request(
                "GET",
                "student_card_progress?user_id=eq.$userId" +
                    "&select=${cardSelect(extended)}&order=last_reviewed_at.asc,flashcard_id.asc" +
                    "&limit=$PAGE_SIZE&offset=$offset",
                token
            )
        )
''', "fetchCards")
    start = s.index("    private suspend fun pull(studentId: String, userId: String, token: String) {")
    end = s.index("\n    private fun cardJson(", start)
    s = s[:start] + '''    private suspend fun pull(studentId: String, userId: String, token: String) {
        var offset = 0
        while (true) {
            val rows = fetchCards(userId, offset, token)
            if (rows.length() == 0) break
            val remote = (0 until rows.length()).map { rows.getJSONObject(it) }
            val ids = remote.map { it.getString("flashcard_id") }
            val existing = ids.chunked(500).flatMap { dao.existingFlashcardIds(it) }.toSet()
            val local = ids.chunked(500).flatMap { dao.progressByIds(studentId, it) }.associateBy { it.flashcardId }
            val toSave = remote.mapNotNull { r ->
                val id = r.getString("flashcard_id")
                val reviewedAt = r.getLong("last_reviewed_at")
                if (id !in existing) return@mapNotNull null
                val mine = local[id]
                if (mine != null && (mine.lastReviewedAt ?: 0L) >= reviewedAt) null
                else CardProgressEntity(
                    studentId = studentId,
                    flashcardId = id,
                    easeFactor = r.getDouble("ease_factor").toFloat(),
                    intervalDays = r.getInt("interval_days"),
                    repetitions = r.getInt("repetitions"),
                    dojoStage = r.optInt("dojo_stage", -1).takeIf { it >= 0 }
                        ?: ci.kalyno.app.data.dojo.DojoStagePolicy.fromRepetitions(r.getInt("repetitions")),
                    dueDate = r.getLong("due_date"),
                    lastReviewedAt = reviewedAt,
                    stability = r.optDouble("stability", 0.0),
                    difficulty = r.optDouble("difficulty", 0.0),
                    lapses = r.optInt("lapses", 0),
                    successCount = r.optInt("success_count", 0),
                    failCount = r.optInt("fail_count", 0),
                    relearnLeft = r.optInt("relearn_left", 0),
                    avgLatencyMs = r.optLong("avg_latency_ms", 0L)
                )
            }
            if (toSave.isNotEmpty()) dao.upsertProgress(toSave)
            offset += rows.length()
            if (rows.length() < PAGE_SIZE) break
        }
    }
''' + s[end:]

    # Rejouer toutes les progressions locales : Supabase fait un upsert sur user_id + flashcard_id.
    start = s.index("    private suspend fun push(studentId: String, userId: String, token: String) {")
    end = s.index("\n    // ------------------------------------------------------------------ Historique des révisions", start)
    s = s[:start] + '''    private suspend fun push(studentId: String, userId: String, token: String) {
        dao.allReviewedProgress(studentId).chunked(PUSH_BATCH).forEach { batch ->
            postCards(batch, userId, token)
        }
    }
''' + s[end:]

    # Historique : pagination par offset pour ne pas sauter les horodatages égaux.
    s = once(s,
'''    private fun fetchLogs(userId: String, since: Long, token: String): JSONArray {
        fun get(extended: Boolean) = JSONArray(
            SupabaseRest.request(
                "GET",
                "student_review_log?user_id=eq.$userId&reviewed_at=gt.$since" +
                    "&select=${logSelect(extended)}&order=reviewed_at.asc&limit=$PAGE_SIZE",
                token
            )
        )
''',
'''    private fun fetchLogs(userId: String, offset: Int, token: String): JSONArray {
        fun get(extended: Boolean) = JSONArray(
            SupabaseRest.request(
                "GET",
                "student_review_log?user_id=eq.$userId" +
                    "&select=${logSelect(extended)}&order=reviewed_at.asc,flashcard_id.asc" +
                    "&limit=$PAGE_SIZE&offset=$offset",
                token
            )
        )
''', "fetchLogs")
    start = s.index("    private suspend fun pullLogs(studentId: String, userId: String, token: String) {")
    end = s.index("\n    private fun postLogs(", start)
    s = s[:start] + '''    private suspend fun pullLogs(studentId: String, userId: String, token: String) {
        var offset = 0
        while (true) {
            val rows = fetchLogs(userId, offset, token)
            if (rows.length() == 0) break
            val items = (0 until rows.length()).mapNotNull { i ->
                val r = rows.getJSONObject(i)
                val rating = runCatching { ReviewRating.valueOf(r.getString("rating")) }.getOrNull()
                    ?: return@mapNotNull null
                ReviewLogEntity(
                    studentId = studentId,
                    flashcardId = r.getString("flashcard_id"),
                    rating = rating,
                    intervalBefore = r.getInt("interval_before"),
                    intervalAfter = r.getInt("interval_after"),
                    reviewedAt = r.getLong("reviewed_at"),
                    latencyMs = r.optLong("latency_ms", 0L),
                    retrievabilityBefore = r.optDouble("retrievability_before", 0.0)
                )
            }
            if (items.isNotEmpty()) dao.insertLogs(items)
            offset += rows.length()
            if (rows.length() < PAGE_SIZE) break
        }
    }
''' + s[end:]

    anchor = "    // ------------------------------------------------------------------ Leçons terminées\n"
    dojo = '''    // ------------------------------------------------------------------ Tentatives du Dojo

    /** Clé déterministe : le rejeu d'une même tentative ne crée jamais une seconde ligne cloud. */
    private fun dojoAttemptClientId(userId: String, a: DojoAttemptEntity): String =
        "$userId:${a.flashcardId}:${a.attemptedAt}:${a.attemptNumber}"

    private fun dojoAttemptJson(a: DojoAttemptEntity, userId: String): JSONObject =
        JSONObject()
            .put("client_attempt_id", dojoAttemptClientId(userId, a))
            .put("user_id", userId)
            .put("flashcard_id", a.flashcardId)
            .put("attempted_at", a.attemptedAt)
            .put("is_success", a.isSuccess)
            .put("attempt_number", a.attemptNumber)
            .put("rating", a.rating.name)
            .put("kind", a.kind)
            .put("question", a.question)
            .put("student_answer", a.studentAnswer ?: JSONObject.NULL)
            .put("reference_answer", a.referenceAnswer)
            .put("lesson_title", a.lessonTitle)
            .put("subject_name", a.subjectName)
            .put("is_legacy", a.isLegacy)

    private suspend fun pushDojoAttempts(studentId: String, userId: String, token: String) {
        attemptDao.allForStudent(studentId).chunked(PUSH_BATCH).forEach { batch ->
            val body = JSONArray()
            batch.forEach { body.put(dojoAttemptJson(it, userId)) }
            SupabaseRest.request(
                "POST",
                "student_dojo_attempts?on_conflict=client_attempt_id",
                token,
                body.toString(),
                IGNORE_DUPLICATES
            )
        }
    }

    /** Restaure les tentatives enregistrées sur les autres appareils ; Room ignore les doublons. */
    private suspend fun pullDojoAttempts(studentId: String, userId: String, token: String) {
        var offset = 0
        while (true) {
            val rows = JSONArray(
                SupabaseRest.request(
                    "GET",
                    "student_dojo_attempts?user_id=eq.$userId" +
                        "&select=flashcard_id,attempted_at,is_success,attempt_number,rating,kind,question," +
                        "student_answer,reference_answer,lesson_title,subject_name,is_legacy" +
                        "&order=attempted_at.asc,client_attempt_id.asc&limit=$PAGE_SIZE&offset=$offset",
                    token
                )
            )
            if (rows.length() == 0) break
            val items = (0 until rows.length()).mapNotNull { i ->
                val r = rows.getJSONObject(i)
                val rating = runCatching { ReviewRating.valueOf(r.getString("rating")) }.getOrNull()
                    ?: return@mapNotNull null
                DojoAttemptEntity(
                    studentId = studentId,
                    flashcardId = r.getString("flashcard_id"),
                    attemptedAt = r.getLong("attempted_at"),
                    isSuccess = r.getBoolean("is_success"),
                    attemptNumber = r.optInt("attempt_number", 0),
                    rating = rating,
                    kind = r.getString("kind"),
                    question = r.getString("question"),
                    studentAnswer = if (r.isNull("student_answer")) null else r.getString("student_answer"),
                    referenceAnswer = r.getString("reference_answer"),
                    lessonTitle = r.optString("lesson_title", ""),
                    subjectName = r.optString("subject_name", ""),
                    isLegacy = r.optBoolean("is_legacy", false)
                )
            }
            if (items.isNotEmpty()) attemptDao.insertAll(items)
            offset += rows.length()
            if (rows.length() < PAGE_SIZE) break
        }
    }

'''
    s = once(s, anchor, dojo + anchor, "fonctions Dojo")
write(p, s)

# Signaler les erreurs actuellement avalées dans DojoViewModel.
p = FILES[3]; s = read(p)
if 'Log.e("KalynoDojo"' not in s:
    s = once(s, "import android.os.SystemClock\n",
             "import android.os.SystemClock\nimport android.util.Log\n", "import Log")
    s = once(s,
'''                } catch (e: Exception) {
                    // tentative non enregistrée : la session continue
                }
''',
'''                } catch (e: Exception) {
                    Log.e("KalynoDojo", "Échec d'enregistrement local d'une tentative Dojo", e)
                }
''', "erreur tentative")
    s = once(s,
'''                } catch (e: Exception) {
                    // Une écriture perdue ne doit pas bloquer la session
                }
''',
'''                } catch (e: Exception) {
                    Log.e("KalynoDojo", "Échec d'enregistrement de la progression FSRS", e)
                }
''', "erreur FSRS")
write(p, s)

check = run("git", "diff", "--check")
if check.returncode:
    sys.exit("Le contrôle git diff --check a échoué :\n" + check.stdout + check.stderr)
print("\nCorrectif appliqué. Sauvegarde :", backup)
print("Aucun commit ni push effectué.")
print("Contrôle suivant : git diff --stat && git diff")
