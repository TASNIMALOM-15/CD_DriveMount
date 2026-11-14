package com.drivemount.app.ui.accounts

/** একটি ইনপুট ঘর */
data class SpecField(
    val param: String,
    val label: String,
    val secret: Boolean = false,
    val optional: Boolean = false,
    val default: String = "",
)

/**
 * সাধারণ প্রোভাইডার বর্ণনা। key = UI/আইকনের slug (ic_cloud_<key>), type = rclone backend।
 * oauth = true হলে browser sign-in (client_id/secret ঐচ্ছিক ঘর স্বয়ংক্রিয়ভাবে যোগ হয়)।
 */
data class ProviderSpec(
    val key: String,
    val label: String,
    val type: String,
    val fields: List<SpecField> = emptyList(),
    val fixed: Map<String, String> = emptyMap(),
    val oauth: Boolean = false,
    val verify: Boolean = false,
)

private fun f(p: String, l: String, secret: Boolean = false, opt: Boolean = false, d: String = "") =
    SpecField(p, l, secret, opt, d)

private fun s3(key: String, label: String, provider: String, endpointRequired: Boolean) = ProviderSpec(
    key, label, "s3",
    fields = buildList {
        add(f("access_key_id", "Access key ID"))
        add(f("secret_access_key", "Secret access key", secret = true))
        add(f("endpoint", "Endpoint" + if (endpointRequired) "" else " (optional)", opt = !endpointRequired))
        add(f("region", "Region (optional)", opt = true))
    },
    fixed = mapOf("provider" to provider),
)

private fun dav(key: String, label: String, vendor: String) = ProviderSpec(
    key, label, "webdav",
    fields = listOf(
        f("url", "Server URL (https://…)"),
        f("user", "Username"),
        f("pass", "Password / App password", secret = true),
    ),
    fixed = mapOf("vendor" to vendor),
)

object ProviderSpecs {
    val all: List<ProviderSpec> = listOf(
        // ---- OAuth (browser) ----
        ProviderSpec("box", "Box", "box", oauth = true),
        ProviderSpec("yandex", "Yandex Disk", "yandex", oauth = true),
        ProviderSpec("zoho", "Zoho WorkDrive", "zoho", oauth = true, fixed = mapOf("region" to "com"),
            fields = listOf(f("region", "Region (com / eu / in / jp / com.au)", opt = true, d = "com"))),
        ProviderSpec("hidrive", "HiDrive", "hidrive", oauth = true),
        ProviderSpec("huawei", "Huawei Drive", "huaweidrive", oauth = true),
        ProviderSpec("putio", "Put.io", "putio", oauth = true),
        ProviderSpec("sharefile", "Citrix ShareFile", "sharefile", oauth = true),
        // ---- S3 পরিবার ----
        s3("s3", "Amazon S3", "AWS", false),
        s3("r2", "Cloudflare R2", "Cloudflare", true),
        s3("wasabi", "Wasabi", "Wasabi", true),
        s3("spaces", "DigitalOcean Spaces", "DigitalOcean", true),
        ProviderSpec("b2", "Backblaze B2", "b2",
            fields = listOf(f("account", "Account ID / Key ID"), f("key", "Application key", secret = true))),
        ProviderSpec("azureblob", "Azure Blob Storage", "azureblob",
            fields = listOf(f("account", "Storage account name"), f("key", "Account key", secret = true))),
        ProviderSpec("azurefiles", "Azure Files", "azurefiles",
            fields = listOf(f("account", "Storage account name"), f("key", "Account key", secret = true), f("share_name", "File share name"))),
        ProviderSpec("swift", "OpenStack Swift", "swift",
            fields = listOf(
                f("auth", "Auth URL"), f("user", "Username"), f("key", "Password / API key", secret = true),
                f("tenant", "Tenant / Project (optional)", opt = true), f("region", "Region (optional)", opt = true),
            )),
        ProviderSpec("storj", "Storj", "storj", fixed = mapOf("provider" to "existing"),
            fields = listOf(f("access_grant", "Access grant", secret = true))),
        ProviderSpec("qingstor", "QingStor", "qingstor",
            fields = listOf(f("access_key_id", "Access key ID"), f("secret_access_key", "Secret access key", secret = true),
                f("zone", "Zone (optional, e.g. pek3a)", opt = true))),
        ProviderSpec("archive", "Internet Archive", "internetarchive",
            fields = listOf(f("access_key_id", "Access key"), f("secret_access_key", "Secret key", secret = true))),
        // ---- WebDAV / সার্ভার ----
        dav("nextcloud", "Nextcloud", "nextcloud"),
        dav("owncloud", "ownCloud", "owncloud"),
        ProviderSpec("seafile", "Seafile", "seafile",
            fields = listOf(f("url", "Server URL"), f("user", "Email"), f("pass", "Password", secret = true),
                f("2fa", "2FA code (optional)", opt = true))),
        ProviderSpec("sftp", "SFTP", "sftp",
            fields = listOf(f("host", "Host"), f("user", "Username"), f("port", "Port (optional)", opt = true),
                f("pass", "Password", secret = true))),
        ProviderSpec("smb", "SMB / CIFS", "smb",
            fields = listOf(f("host", "Host"), f("user", "Username"), f("pass", "Password", secret = true),
                f("domain", "Domain (optional)", opt = true), f("port", "Port (optional)", opt = true))),
        ProviderSpec("http", "HTTP", "http", fields = listOf(f("url", "URL (https://…)"))),
        ProviderSpec("hdfs", "HDFS", "hdfs",
            fields = listOf(f("namenode", "Namenode (host:port)"), f("username", "Username (optional)", opt = true))),
        // ---- ইউজারনেম/পাসওয়ার্ড ও API key ----
        ProviderSpec("koofr", "Koofr", "koofr", fixed = mapOf("provider" to "koofr"),
            fields = listOf(f("user", "Email"), f("password", "App password", secret = true)), verify = true),
        ProviderSpec("mailru", "Mail.ru Cloud", "mailru",
            fields = listOf(f("user", "Email"), f("pass", "App password", secret = true)), verify = true),
        ProviderSpec("opendrive", "OpenDrive", "opendrive",
            fields = listOf(f("username", "Username"), f("password", "Password", secret = true)), verify = true),
        ProviderSpec("pikpak", "PikPak", "pikpak",
            fields = listOf(f("user", "Email / Username"), f("pass", "Password", secret = true)), verify = true),
        ProviderSpec("pixeldrain", "Pixeldrain", "pixeldrain", fields = listOf(f("api_key", "API key", secret = true))),
        ProviderSpec("premiumize", "Premiumize.me", "premiumizeme", fields = listOf(f("api_key", "API key", secret = true))),
        ProviderSpec("internxt", "Internxt", "internxt",
            fields = listOf(f("email", "Email"), f("pass", "Password", secret = true)), verify = true),
        ProviderSpec("filelu", "FileLu", "filelu", fields = listOf(f("key", "API key", secret = true))),
        ProviderSpec("filescom", "Files.com", "filescom",
            fields = listOf(f("site", "Site subdomain"), f("username", "Username"), f("password", "Password", secret = true))),
        ProviderSpec("fichier", "1Fichier", "fichier", fields = listOf(f("api_key", "API key", secret = true))),
        ProviderSpec("ulozto", "Uloz.to", "ulozto",
            fields = listOf(f("username", "Username"), f("password", "Password", secret = true),
                f("app_token", "App token (optional)", secret = true, opt = true))),
        ProviderSpec("quatrix", "Quatrix", "quatrix",
            fields = listOf(f("host", "Host"), f("api_key", "API key", secret = true))),
        ProviderSpec("linkbox", "Linkbox", "linkbox", fields = listOf(f("token", "Token", secret = true))),
        ProviderSpec("drime", "Drime", "drime", fields = listOf(f("access_token", "Access token", secret = true))),
        ProviderSpec("dosya", "Dosya", "dosya",
            fields = listOf(f("api_key", "API key", secret = true), f("workspace_id", "Workspace ID (optional)", opt = true))),
        ProviderSpec("shade", "Shade", "shade",
            fields = listOf(f("drive_id", "Drive ID"), f("api_key", "API key", secret = true))),
        ProviderSpec("filefabric", "Enterprise File Fabric", "filefabric",
            fields = listOf(f("url", "URL"), f("permanent_token", "Permanent token", secret = true))),
        ProviderSpec("imagekit", "ImageKit", "imagekit",
            fields = listOf(f("endpoint", "URL endpoint"), f("public_key", "Public key"), f("private_key", "Private key", secret = true))),
        ProviderSpec("cloudinary", "Cloudinary", "cloudinary",
            fields = listOf(f("cloud_name", "Cloud name"), f("api_key", "API key"), f("api_secret", "API secret", secret = true))),
    )

    private val byKey = all.associateBy { it.key }
    fun get(key: String): ProviderSpec? = byKey[key]

    /** রাখা account-এর rclone type থেকে নাম (s3, b2 ...) */
    fun labelForType(type: String): String? = byKey[type]?.label ?: all.firstOrNull { it.type == type }?.label
}
