package com.heypapu.ai.jobs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class JobListing(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val portal: String,
    val category: String,
    val matchScore: Int,
    val matchedSkills: List<String>,
    val applyUrl: String,
    val recruiterEmail: String = "",
    val postedDate: String = "Recent"
)

data class TrackedApplication(
    val id: String,
    val jobTitle: String,
    val company: String,
    val portal: String,
    val status: String,
    val appliedDate: String,
    val interviewDate: String = "Pending"
)

class JobPortalRepository(private val context: Context) {

    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("papu_job_tracker", Context.MODE_PRIVATE)

    suspend fun searchWorldwideJobs(
        query: String,
        userSkillsCsv: String
    ): List<JobListing> = withContext(Dispatchers.IO) {
        val results = mutableListOf<JobListing>()
        val skillTokens = userSkillsCsv.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        val effectiveQuery = query.ifBlank { "React WordPress Web AI Developer" }

        // 1. Live Remotive Worldwide Remote Developer Jobs API
        try {
            val encoded = Uri.encode(effectiveQuery)
            val req = Request.Builder()
                .url("https://remotive.com/api/remote-jobs?search=$encoded&limit=12")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val jobs = json.optJSONArray("jobs") ?: JSONArray()
                    for (i in 0 until jobs.length()) {
                        val j = jobs.getJSONObject(i)
                        val title = j.optString("title")
                        val desc = j.optString("description") + " " + title
                        val (score, matched) = calculateSkillMatch(desc, skillTokens)
                        results.add(
                            JobListing(
                                id = "remotive_${j.optInt("id")}",
                                title = title,
                                company = j.optString("company_name"),
                                location = j.optString("candidate_required_location", "Worldwide"),
                                portal = "Remotive Global",
                                category = j.optString("category", "Software Development"),
                                matchScore = score,
                                matchedSkills = matched,
                                applyUrl = j.optString("url"),
                                postedDate = j.optString("publication_date").take(10)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Live Arbeitnow Tech & AI Jobs API
        try {
            val req = Request.Builder()
                .url("https://www.arbeitnow.com/api/job-board-api")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val data = json.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until minOf(data.length(), 25)) {
                        val j = data.getJSONObject(i)
                        val title = j.optString("title")
                        val desc = j.optString("description") + " " + title
                        if (listOf("react", "wordpress", "web", "frontend", "full stack", "ai", "developer")
                                .any { desc.lowercase().contains(it) }
                        ) {
                            val (score, matched) = calculateSkillMatch(desc, skillTokens)
                            results.add(
                                JobListing(
                                    id = "arbeit_${j.optString("slug")}",
                                    title = title,
                                    company = j.optString("company_name"),
                                    location = j.optString("location", "Remote / Global"),
                                    portal = "Arbeitnow Tech",
                                    category = "Web / AI Engineering",
                                    matchScore = score,
                                    matchedSkills = matched,
                                    applyUrl = j.optString("url"),
                                    postedDate = "Today"
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 3. Direct Verified Portal Search Cards for LinkedIn, Naukri & Indeed
        val portalQueries = listOf(
            Triple("LinkedIn Jobs", "React & WordPress Full-Stack Developer", "https://www.linkedin.com/jobs/search/?keywords=${Uri.encode(effectiveQuery)}"),
            Triple("Naukri India", "WordPress & React Web Developer", "https://www.naukri.com/${effectiveQuery.lowercase().replace(" ", "-")}-jobs"),
            Triple("Indeed Worldwide", "Frontend React & AI Web Developer", "https://www.indeed.com/jobs?q=${Uri.encode(effectiveQuery)}")
        )
        portalQueries.forEachIndexed { idx, (portal, roleTitle, url) ->
            results.add(
                JobListing(
                    id = "portal_$idx",
                    title = "$roleTitle ($effectiveQuery)",
                    company = "Live Search on $portal",
                    location = "India / Remote / Worldwide",
                    portal = portal,
                    category = "Verified Job Portal",
                    matchScore = 94 - (idx * 3),
                    matchedSkills = listOf("React", "WordPress", "Web Development", "AI"),
                    applyUrl = url,
                    postedDate = "Live Feed"
                )
            )
        }

        results.sortedByDescending { it.matchScore }
    }

    private fun calculateSkillMatch(text: String, skills: List<String>): Pair<Int, List<String>> {
        val lower = text.lowercase()
        val matched = skills.filter { lower.contains(it) }.distinct().take(6)
        val base = 62 + (matched.size * 7)
        return Pair(minOf(base, 98), matched.ifEmpty { listOf("Web Development", "Full-Stack") })
    }

    fun generateTailoredResumeAndCoverLetter(
        candidateName: String,
        candidateEmail: String,
        skills: String,
        experience: String,
        job: JobListing
    ): Pair<String, File> {
        val today = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        val coverLetter = """
            Date: $today
            To: Hiring Team, ${job.company}
            Subject: Application for ${job.title} — $candidateName

            Dear Hiring Manager,

            I am excited to apply for the ${job.title} position at ${job.company} (found via ${job.portal}). With strong hands-on experience in $skills, I specialize in building fast, responsive, SEO-ready web applications, custom WordPress solutions, and AI-assisted digital platforms.

            Key Highlights Aligned With Your Role:
            • $experience
            • Matched Core Competencies: ${job.matchedSkills.joinToString(", ")}
            • Proven track record delivering clean UI/UX, scalable CMS portals, and modern React architectures.

            I have attached my tailored ATS-optimized resume for your review and would welcome the opportunity to discuss how I can contribute to ${job.company}.

            Warm regards,
            $candidateName
            ${if (candidateEmail.isNotBlank()) "Email: $candidateEmail" else ""}
        """.trimIndent()

        val resumeContent = """
            ====================================================================
            $candidateName
            Full-Stack Web Developer | React | WordPress CMS | AI Integration
            ${if (candidateEmail.isNotBlank()) "Email: $candidateEmail | " else ""}Location: India (Available Worldwide / Remote)
            ====================================================================

            PROFESSIONAL SUMMARY
            $experience

            CORE TECHNICAL SKILLS
            $skills

            TARGET ROLE ALIGNMENT: ${job.title} @ ${job.company}
            Matched Keywords: ${job.matchedSkills.joinToString(" | ")}

            KEY PROJECTS & EXPERIENCE
            • Enterprise & Academic Web Portals: Built interactive study and institutional portals featuring mock tests, course libraries, and AI assistant widgets.
            • WordPress & Custom CMS Engineering: Developed custom themes, plugins, performance optimizations, and WooCommerce/business workflows.
            • React & Modern Frontend Architecture: Engineered component-driven SPAs with REST API integration, responsive layouts, and ATS-grade structured data.

            COVER LETTER
            $coverLetter
        """.trimIndent()

        val dir = File(context.filesDir, "resumes").apply { mkdirs() }
        val safeCompany = job.company.replace(Regex("[^A-Za-z0-9]"), "_").take(20)
        val resumeFile = File(dir, "${candidateName.replace(" ", "_")}_${safeCompany}_Resume.txt")
        resumeFile.writeText(resumeContent)

        return Pair(coverLetter, resumeFile)
    }

    fun launchApprovedJobApplication(job: JobListing, coverLetter: String, resumeFile: File) {
        if (job.recruiterEmail.isNotBlank()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                resumeFile
            )
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(job.recruiterEmail))
                putExtra(Intent.EXTRA_SUBJECT, "Job Application: ${job.title}")
                putExtra(Intent.EXTRA_TEXT, coverLetter)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(emailIntent, "Send Approved Job Email").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(job.applyUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
        recordTrackedApplication(
            TrackedApplication(
                id = job.id,
                jobTitle = job.title,
                company = job.company,
                portal = job.portal,
                status = "Applied (User Approved)",
                appliedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
                interviewDate = "Awaiting Recruiter"
            )
        )
    }

    fun getTrackedApplications(): List<TrackedApplication> {
        val raw = prefs.getString("tracked_list", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<TrackedApplication>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                TrackedApplication(
                    id = o.optString("id"),
                    jobTitle = o.optString("jobTitle"),
                    company = o.optString("company"),
                    portal = o.optString("portal"),
                    status = o.optString("status"),
                    appliedDate = o.optString("appliedDate"),
                    interviewDate = o.optString("interviewDate")
                )
            )
        }
        return list
    }

    fun recordTrackedApplication(app: TrackedApplication) {
        val current = getTrackedApplications().filterNot { it.id == app.id }.toMutableList()
        current.add(0, app)
        val arr = JSONArray()
        current.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("jobTitle", it.jobTitle)
                put("company", it.company)
                put("portal", it.portal)
                put("status", it.status)
                put("appliedDate", it.appliedDate)
                put("interviewDate", it.interviewDate)
            })
        }
        prefs.edit().putString("tracked_list", arr.toString()).apply()
    }
}
