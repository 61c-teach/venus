package venus

/* ktlint-disable no-wildcard-imports */

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder
import io.javalin.http.Context
import io.javalin.plugin.json.JavalinJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.util.ssl.SslContextFactory
import venus.fernet.Fernet
import venus.fernet.FernetException
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList
import kotlin.system.exitProcess

/* ktlint-enable no-wildcard-imports */

val VENUS_FS_API_PATH = "/api/fs"
val VENUS_FS_VERSION = "1.0.2"

val VENUS_URL = "https://venus.cs61c.org"

val MESSAGE_TTL = 30

// By default we bind to loopback - this may become configurable in the future.
val DEFAULT_HOST = "localhost"

fun getRandomString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
}

class Mounter(var port: String, var dir: String, var key_path: String = System.getProperty("user.home") + "/.venus_mount_key", var custkey: String? = null, var host: String = DEFAULT_HOST, var message_ttl: Int = MESSAGE_TTL) {
//    data class LoginToken(var token: String, var expiration: String)
//    val tokens: MutableMap<String, String>

    private val baseAbsPath: Path

    val fernet: Fernet

    val sendString = getRandomString(64)
    val responseString = getRandomString(64)

    val connect_message: String
    val connect_command: String

    /**
     * Checks that a path is within the directory that was mounted.
     * This mitigates potential RCE vulnerabilities by preventing file operations to paths like "~/.bashrc".
     * All file operations should call this function.
     *
     * @param targetPath the path of the file to access, relative to the directory the mount was initialized to
     * @param names additional components of the path of the file to be found
     * @param verbose prints the absolute path of the requested file if true
     * @returns the file object if access is allowed, otherwise null
     */
    private fun validateFilePath(targetPath: String, vararg names: String, verbose: Boolean = true): File? {
        val fpath = Paths.get(System.getProperty("user.dir"), targetPath, *names).normalize()
        if (verbose) {
            println(fpath.toUri())
        }
        val fp = File(fpath.toUri())
        return if (!fpath.startsWith(baseAbsPath)) {
            null
        } else {
            fp
        }
    }

    fun fernetEncrypt(data: String): String {
        val bytes = ArrayList<Byte>(data.length)
        for (c in data) {
            bytes.add(c.toByte())
        }
        return fernet.encrypt(bytes.toByteArray())
    }

    fun fernetDecrypt(data: String): String? {
        return try {
            val dec = fernet.decrypt(data, message_ttl)
            val sb = java.lang.StringBuilder()
            for (b in dec) {
                val s = b.toUByte().toShort()
                sb.append(s.toChar())
            }
            sb.toString()
        } catch (e: FernetException) {
            println(e)
            null
        }
    }

    @Serializable
    data class GenericRequest(val data: String)
    data class GenericResponse(val success: Boolean, val data: Any)

    fun encryptAndSendJsonToContext(ctx: Context, obj: Any) {
        val msg = GenericResponse(true, fernetEncrypt(JavalinJson.toJson(obj)))
        ctx.json(msg)
    }

    init {
// //        val key = Fernet.Key("cw_0x689RpI-jtRR7oE8h_eQsKImvJapLeSbXpwF4e4=")
// //        val token = Fernet.Token(Fernet.time, byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
//
//        val fernet = Fernet("cw_0x689RpI-jtRR7oE8h_eQsKImvJapLeSbXpwF4e4=")
//
//
//
// //        val token = fernet.encrypt("The quick brown fox jumps over the lazy dog.".toByteArray())
// //        println("Token = $token")
//        val token = "gAAAAABgCjgs5fAF5QOd8jJkEaogu9g5w20lbAMcaWIqlMYLEO4iK3TJgPVTQXWHJCCcNGNkPrGtx9wIaRbHfFglj4RNP-vtHw=="
//        val message = fernet.decrypt(token)
//        println("Message = " + String(message))
        var key: String
        if (custkey == null) {
            var kp = File(key_path)
            if (kp.exists()) {
                // Load key
                key = kp.readText()
            } else {
                // Create key and save it
                key = Fernet.Key().toString()
                kp.createNewFile()
                kp.writeText(key)
            }
        } else {
            key = custkey!!
        }
        fernet = Fernet(key)
        connect_command = "mount http://$host:$port vmfs $key"
        connect_message = "To connect, enter `$connect_command` on Venus."
        println(connect_message)
        val fdir = File(dir)
        if (!fdir.exists() or !fdir.isDirectory) {
            System.err.println("The passed in dir is not a directory: $dir")
            exitProcess(1)
        }
        System.setProperty("user.dir", fdir.absolutePath)
        baseAbsPath = Paths.get(fdir.absolutePath).normalize()

        println("Message TTL set to: $message_ttl")

        val app: Javalin = Javalin.create { config ->
            config.enableCorsForAllOrigins()
            config.server {
                val server = Server()
                val connector = ServerConnector(server)
                connector.port = port.toInt()
                server.connectors = arrayOf<Connector>(connector)
//                val sslConnector = ServerConnector(server, sslContextFactory())
//                sslConnector.port = port.toInt() + 1
//                server.connectors = arrayOf<Connector>(sslConnector, connector)
                server
            }
        }.start(host, port.toInt())
        app.routes {
//            post("/login") { ctx ->
//                val auth_token = ctx.body()
//                if (auth_token == VENUS_AUTH_TOKEN) {
//                    ctx.json()
//                }
//            }
            ApiBuilder.get("/") { ctx ->
                ctx.html("Welcome to the Venus mount server!<br><br>If you are unable to connect to the mount server via the official <a href=\"$VENUS_URL\">Venus website</a>, you can try using a <a href=\"/venus\">local proxy through this server</a>. Please note that these two websites do NOT share settings or files so you will need to work in only one. If you change the port of the mount server, your settings will also not be preserved!")
            }
            ApiBuilder.get("/venus") { ctx ->
                val url = URL(VENUS_URL)
                ctx.html(url.readText())
            }
            ApiBuilder.get("/css/*") { ctx ->
                proxy(ctx)
            }
            ApiBuilder.get("/images/*") { ctx ->
                proxy(ctx)
            }
            ApiBuilder.get("/js/*") { ctx ->
                proxy(ctx)
            }
            ApiBuilder.get("/jvm/*") { ctx ->
                proxy(ctx)
            }
            ApiBuilder.get("/packages/*") { ctx ->
                proxy(ctx)
            }
            ApiBuilder.get("/scripts/*") { ctx ->
                proxy(ctx)
            }
            ApiBuilder.get("/version") { ctx ->
                println("Got version request from ${ctx.ip()}...")
                ctx.json(mapOf(Pair("data", VENUS_FS_VERSION)))
            }
            ApiBuilder.get("/ping") { ctx ->
                println("Got ping request from ${ctx.ip()}! Ponging...")
                ctx.json(mapOf(Pair("data", "pong")))
            }
            ApiBuilder.get("/showkey") { ctx ->
                println("An application from ${ctx.ip()} is requesting to connect. If requested, please enter in this key to continue with the connection: $key")
                ctx.json(mapOf(Pair("msg", "Key has been shown in the Venus mount server! Please copy and paste it into here."), Pair("send", sendString), Pair("response", responseString)))
            }
            @Serializable
            data class AuthMessage(val msg: String, val send: String, val response: String)
            ApiBuilder.post("/v1/auth") { ctx ->
                val rdat = ctx.body()
                println("Auth request.")
                try {
                    val req = Json.parse(AuthMessage.serializer(), rdat)
                    if (req.msg != "verify") {
                        ctx.json(AuthMessage("Unknown auth command: ${req.msg}!", "", ""))
                    } else {
                        val decSent = fernetDecrypt(req.send)
                        if (decSent != sendString) {
                            ctx.json(AuthMessage("Your connection key is incorrect!", "", ""))
                        } else {
                            ctx.json(AuthMessage("", "", fernetEncrypt(responseString)))
                        }
                    }
                } catch (e: Exception) {
                    ctx.json(AuthMessage("Internal server error: $e", "", ""))
                    println("ERROR: $e")
                }
            }
            ApiBuilder.get("$VENUS_FS_API_PATH/name") { ctx ->
                encryptAndSendJsonToContext(ctx, GenericResponse(success = true, data = "lvfs"))
            }
            ApiBuilder.post("$VENUS_FS_API_PATH/ls/names") { ctx ->
                print("ls request: ")
                val rdat = fernetDecrypt(ctx.body())
                if (rdat == null) {
                    println("Failed to decrypt request! Make sure you are using the correct key: $key")
                    ctx.json(GenericResponse(false, "Internal server error: Failed to decrypt request! Is your key out of date?"))
                    return@post
                }
                try {
                    val req = Json.parse(GenericRequest.serializer(), rdat)
                    var filepath = req.data
                    if (filepath == "") {
                        filepath = "."
                    }
                    val fp = validateFilePath(filepath)
                    if (fp == null) {
                        encryptAndSendJsonToContext(ctx, GenericResponse(false, "$filepath: No such file or directory"))
                    } else {
                        val list = mutableListOf<Array<String>>()
                        for (f in fp.listFiles()) {
                            list.add(arrayOf(f.name, if (f.isFile) { "file" } else { "dir" }))
                        }
                        encryptAndSendJsonToContext(ctx, GenericResponse(true, list))
                    }
                } catch (e: Exception) {
                    encryptAndSendJsonToContext(ctx, GenericResponse(false, "Internal server error: $e"))
                    println("ERROR: $e")
                }
            }
            @Serializable
            data class fileinfoRequest(val name: String, val path: String)
            data class fileinfoResponse(val success: Boolean, val name: String = "", val type: String = "", val data: String = "")
            ApiBuilder.post("$VENUS_FS_API_PATH/file/info") { ctx ->
                print("file info request: ")
                val rdat = fernetDecrypt(ctx.body())
                if (rdat == null) {
                    println("Failed to decrypt request!")
                    ctx.json(GenericResponse(false, "Internal server error: Failed to decrypt request! Is your key out of date?"))
                    return@post
                }
                try {
                    val req = Json.parse(fileinfoRequest.serializer(), rdat)
                    val name = req.name
                    val fp = validateFilePath(req.path, name)
                    if (fp == null) {
                        encryptAndSendJsonToContext(ctx, fileinfoResponse(false, data = "$name: No such file or directory"))
                    } else {
                        encryptAndSendJsonToContext(ctx, fileinfoResponse(true, name = name, type = if (fp.isFile) { "file" } else { "dir" }))
                    }
                } catch (e: Exception) {
                    encryptAndSendJsonToContext(ctx, fileinfoResponse(false, "Internal server error: $e"))
                    println("ERROR: $e")
                }
            }

            @Serializable
            data class filereadRequest(val path: String)
            data class filereadResponse(val success: Boolean, val data: String)
            ApiBuilder.post("$VENUS_FS_API_PATH/file/read") { ctx ->
                val rdat = fernetDecrypt(ctx.body())
                if (rdat == null) {
                    println("Failed to decrypt request!")
                    ctx.json(GenericResponse(false, "Internal server error: Failed to decrypt request! Is your key out of date?"))
                    return@post
                }
                print("file read request: ")
                try {
                    val req = Json.parse(filereadRequest.serializer(), rdat)
                    val path = req.path
                    val fp = validateFilePath(path)
                    if (fp == null) {
                        encryptAndSendJsonToContext(ctx, filereadResponse(false, data = "$path: No such file or directory"))
                    } else if (!fp.isFile) {
                        encryptAndSendJsonToContext(ctx, filereadResponse(false, data = "$path: Is not file"))
                    } else {
                        val raw = fp.readBytes()
//                        val data = StringBuilder()
//                        for (byte in raw) {
//                            data.append(byte.toChar())
//                        }
//                        encryptAndSendJsonToContext(ctx, filereadResponse(true, data = Base64.getEncoder().encodeToString(data.toString().toByteArray())))\
                        encryptAndSendJsonToContext(ctx, filereadResponse(true, data = Base64.getEncoder().encodeToString(raw)))
                    }
                } catch (e: Exception) {
                    encryptAndSendJsonToContext(ctx, filereadResponse(false, "Internal server error: $e"))
                    println("ERROR: $e")
                }
            }

            @Serializable
            data class filewriteRequest(val path: String, val data: String)
            data class filewriteResponse(val success: Boolean, val data: String)
            ApiBuilder.post("$VENUS_FS_API_PATH/file/write") { ctx ->
                val rdat = fernetDecrypt(ctx.body())
                if (rdat == null) {
                    println("Failed to decrypt request!")
                    ctx.json(GenericResponse(false, "Internal server error: Failed to decrypt request! Is your key out of date?"))
                    return@post
                }
                print("file write request: ")
                try {
                    val req = Json.parse(filewriteRequest.serializer(), rdat)
                    val path = req.path
                    val fp = validateFilePath(path)
                    if (fp == null) {
                        encryptAndSendJsonToContext(ctx, filewriteResponse(false, data = "$path: No such file or directory"))
                    } else if (!fp.isFile) {
                        encryptAndSendJsonToContext(ctx, filewriteResponse(false, data = "$path: No such file or directory"))
                    } else {
//                        val bytearr = ByteArrayBuilder()
//                        for (char in req.data.chars()) {
//                            bytearr.append(char)
//                        }
//                        fp.writeBytes(bytearr.toByteArray())
                        val decoded = Base64.getDecoder().decode(req.data)
                        fp.writeBytes(decoded)
                        encryptAndSendJsonToContext(ctx, filewriteResponse(true, data = ""))
                    }
                } catch (e: Exception) {
                    encryptAndSendJsonToContext(ctx, filewriteResponse(false, "Internal server error: $e"))
                    println("ERROR: $e")
                }
            }
            // Functionality needed: rm, mv, cp

            @Serializable
            data class mkdirRequest(val path: String)
            data class mkdirResponse(val success: Boolean, val data: String)
            ApiBuilder.post("$VENUS_FS_API_PATH/mkdir") { ctx ->
                val rdat = fernetDecrypt(ctx.body())
                if (rdat == null) {
                    println("Failed to decrypt request!")
                    ctx.json(GenericResponse(false, "Internal server error: Failed to decrypt request! Is your key out of date?"))
                    return@post
                }
                print("mkdir request: ")
                try {
                    val req = Json.parse(mkdirRequest.serializer(), rdat)
                    val path = req.path
                    val fp = validateFilePath(path)
                    val s = if (fp != null) {
                        if (fp.exists()) {
                            encryptAndSendJsonToContext(ctx, mkdirResponse(false, data = "$path: Already exists"))
                        }
                        fp.mkdir()
                    } else {
                        false
                    }
                    encryptAndSendJsonToContext(ctx, mkdirResponse(s, if (!s) { "$path: Failed to create the directory" } else { "" }))
                } catch (e: Exception) {
                    encryptAndSendJsonToContext(ctx, mkdirResponse(false, "Internal server error: $e"))
                    println("ERROR: $e")
                }
            }

            @Serializable
            data class touchRequest(val path: String)
            data class touchResponse(val success: Boolean, val data: String)
            ApiBuilder.post("$VENUS_FS_API_PATH/touch") { ctx ->
                val rdat = fernetDecrypt(ctx.body())
                if (rdat == null) {
                    println("Failed to decrypt request!")
                    ctx.json(GenericResponse(false, "Internal server error: Failed to decrypt request! Is your key out of date?"))
                    return@post
                }
                print("touch request: ")
                try {
                    val req = Json.parse(touchRequest.serializer(), rdat)
                    val path = req.path
                    val fp = validateFilePath(path)
                    val s = if (fp != null) {
                        if (fp.exists()) {
                            encryptAndSendJsonToContext(ctx, touchResponse(false, data = "$path: Already exists"))
                        }
                        fp.createNewFile()
                    } else {
                        false
                    }
                    encryptAndSendJsonToContext(ctx, touchResponse(s, if (!s) { "$path: Failed to create the file" } else { "" }))
                } catch (e: Exception) {
                    encryptAndSendJsonToContext(ctx, touchResponse(false, "Internal server error: $e"))
                    println("ERROR: $e")
                }
            }

            @Serializable
            data class rmRequest(val path: String)
            data class rmResponse(val success: Boolean, val data: String)
            ApiBuilder.post("$VENUS_FS_API_PATH/rm") { ctx ->
                val rdat = fernetDecrypt(ctx.body())
                if (rdat == null) {
                    println("Failed to decrypt request!")
                    ctx.json(GenericResponse(false, "Internal server error: Failed to decrypt request! Is your key out of date?"))
                    return@post
                }
                print("rm request: ")
                try {
                    val req = Json.parse(rmRequest.serializer(), rdat)
                    val path = req.path
                    val fp = validateFilePath(path)
                    val s = if (fp != null) {
                        if (!fp.exists()) {
                            encryptAndSendJsonToContext(ctx, mkdirResponse(false, data = "$path: No such file or directory"))
                        }
                        fp.deleteRecursively()
                    } else {
                        false
                    }
                    encryptAndSendJsonToContext(ctx, rmResponse(s, if (!s) { "$path: Failed to delete the file or directory" } else { "" }))
                } catch (e: Exception) {
                    encryptAndSendJsonToContext(ctx, rmResponse(false, "Internal server error: $e"))
                    println("ERROR: $e")
                }
            }
        }
    }

    private fun sslContextFactory(): SslContextFactory? {
        return SslContextFactory().apply {
            keyStorePath = Mounter::class.java.getResource("/keystore.jks").toExternalForm() // replace with your real keystore
            setKeyStorePassword("password") // replace with your real password
        }
    }

    private fun proxy(ctx: Context) {
        val urlpath = VENUS_URL + ctx.path()
//        println("Proxying connection to: $urlpath")
        val url = URL(urlpath)
        val connection = url.openConnection()
        if (connection != null) {
            try {
                val input_stream = connection.getInputStream()
                ctx.contentType(connection.contentType)
                ctx.result(input_stream.readAllBytes())
            } catch (e: FileNotFoundException) {
                ctx.status(404)
            }
        } else {
            ctx.status(404)
        }
    }
}
