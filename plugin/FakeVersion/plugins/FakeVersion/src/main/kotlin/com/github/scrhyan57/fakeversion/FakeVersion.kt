package com.github.scrhyan57.fakeversion

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import org.json.JSONObject
import java.util.regex.Pattern

@AliucordPlugin(requiresRestart = false)
@Suppress("unused")
class FakeVersion : Plugin() {

    private val fallbackVersionName = "331.14-stable"
    private val fallbackVersionCode = 331140

    @Volatile private var spoofedVersionName = fallbackVersionName
    @Volatile private var spoofedVersionCode = fallbackVersionCode

    override fun start(context: Context) {
        fetchLatestVersion()
        patchPackageManager(context)
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    private fun fetchLatestVersion() {
        Utils.threadPool.execute {
            try {
                val response = Http.Request("https://www.apkmirror.com/apk/discord-inc/discord-chat-for-gamers/", "GET")
                    .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .execute()

                val body = response.text()
                val pattern = Pattern.compile("""(\d{3,4}\.\d{1,2})-[Ss]table""")
                val matcher = pattern.matcher(body)

                if (matcher.find()) {
                    val versionStr = matcher.group(1)!!
                    val parts = versionStr.split(".")
                    val major = parts[0].toIntOrNull() ?: return@execute
                    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val computed = major * 1000 + minor

                    spoofedVersionName = "$versionStr-stable"
                    spoofedVersionCode = computed

                    logger.info("FakeVersion: fetched latest version -> $spoofedVersionName ($spoofedVersionCode)")
                } else {
                    logger.warn("FakeVersion: could not parse version from APKMirror, using fallback")
                }
            } catch (e: Throwable) {
                logger.warn("FakeVersion: network fetch failed, using fallback", e)
            }
        }
    }

    private fun patchPackageManager(context: Context) {
        val discordPackage = context.packageName

        patcher.after<PackageManager>(
            "getPackageInfo",
            String::class.java,
            Int::class.javaPrimitiveType!!,
        ) {
            val pkgName = it.args[0] as? String ?: return@after
            if (pkgName != discordPackage) return@after
            val info = it.result as? PackageInfo ?: return@after
            applySpoof(info)
        }

        patcher.after<PackageManager>(
            "getPackageInfo",
            String::class.java,
            PackageManager.PackageInfoFlags::class.java,
        ) {
            val pkgName = it.args[0] as? String ?: return@after
            if (pkgName != discordPackage) return@after
            val info = it.result as? PackageInfo ?: return@after
            applySpoof(info)
        }
    }

    private fun applySpoof(info: PackageInfo) {
        try {
            val versionNameField = PackageInfo::class.java.getField("versionName")
            versionNameField.set(info, spoofedVersionName)
        } catch (_: Throwable) {}

        try {
            val versionCodeField = PackageInfo::class.java.getField("versionCode")
            versionCodeField.setInt(info, spoofedVersionCode)
        } catch (_: Throwable) {}

        try {
            val longVersionCodeField = PackageInfo::class.java.getField("longVersionCode")
            longVersionCodeField.setLong(info, spoofedVersionCode.toLong())
        } catch (_: Throwable) {}
    }
}
