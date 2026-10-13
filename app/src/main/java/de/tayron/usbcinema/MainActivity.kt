package de.tayron.usbcinema

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.content.ClipData
import android.content.ClipboardManager
import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

class MainActivity : Activity() {
    private val pickCode = 12
    private var tree: Uri? = null
    private var server: CinemaServer? = null
    private lateinit var status: TextView
    private lateinit var address: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24); setBackgroundColor(Color.rgb(17,20,29))
        }
        fun text(s:String, size:Float=16f, color:Int=Color.WHITE) = TextView(this).apply {
            text=s; textSize=size; setTextColor(color); setPadding(0,10,0,10)
        }
        root.addView(text("USB-Cinema",30f,Color.rgb(150,190,255)))
        root.addView(text("Filme vom USB-Stick im selben WLAN teilen",15f,Color.LTGRAY))
        status=text("1. USB-Stick anschließen und Ordner auswählen.")
        root.addView(status)
        root.addView(Button(this).apply { text="USB-Filmordner auswählen"; setOnClickListener {
            val i=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            startActivityForResult(i,pickCode)
        }})
        root.addView(Button(this).apply { text="Streaming starten"; setOnClickListener { startStreaming() }})
        root.addView(Button(this).apply { text="Server stoppen"; setOnClickListener { server?.stop(); server=null; status.text="Server gestoppt." }})
        address=text("Die WLAN-Adresse erscheint hier.",18f,Color.rgb(130,230,170)); root.addView(address)
        root.addView(Button(this).apply { text="Adresse kopieren"; setOnClickListener {
            val a=address.text.toString()
            if(a.startsWith("http")) { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("USB-Cinema",a)); Toast.makeText(this@MainActivity,"Adresse kopiert",Toast.LENGTH_SHORT).show() }
        }})
        root.addView(text("Lass die App geöffnet. Freunde müssen im selben WLAN sein. Gäste-WLANs können Verbindungen zwischen Geräten blockieren.",14f,Color.LTGRAY))
        val sc=ScrollView(this); sc.addView(root); setContentView(sc)
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==pickCode && resultCode==RESULT_OK) {
            tree=data?.data ?: return
            try { contentResolver.takePersistableUriPermission(tree!!,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {}
            status.text="Filmordner ausgewählt. Jetzt Streaming starten."
        }
    }
    private fun startStreaming() {
        val uri=tree
        if(uri==null) { Toast.makeText(this,"Bitte zuerst den USB-Filmordner auswählen.",Toast.LENGTH_LONG).show(); return }
        try {
            server?.stop()
            server=CinemaServer(uri).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT,false) }
            val ip=Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence()
                .filter { it.isUp && !it.isLoopback }.flatMap { Collections.list(it.inetAddresses).asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':')==false && (it.hostAddress!!.startsWith("192.168.") || it.hostAddress!!.startsWith("10.") || it.hostAddress!!.startsWith("172.")) }?.hostAddress
            if(ip==null) { server?.stop(); server=null; status.text="Keine WLAN-IP gefunden. WLAN aktivieren."; return }
            address.text="http://$ip:8765/"
            status.text="Server läuft auf Port 8765."
        } catch(e:Exception) { status.text="Fehler beim Start: ${e.message}" }
    }
    inner class CinemaServer(private val rootUri:Uri):NanoHTTPD("0.0.0.0",8765) {
        override fun serve(session:IHTTPSession):Response {
            val path=Uri.decode(session.uri.trim('/'))
            if(path.isBlank()) return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8",page())
            val wanted=path.removePrefix("video/")
            val doc=findFile(rootUri,wanted) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","Datei nicht gefunden")
            return try {
                val input=contentResolver.openInputStream(doc) ?: throw Exception("Nicht lesbar")
                val ext=wanted.substringAfterLast('.',"").lowercase(Locale.ROOT)
                val mime=when(ext) { "mp4","m4v"->"video/mp4"; "webm"->"video/webm"; "mov"->"video/quicktime"; "mkv"->"video/x-matroska"; "avi"->"video/x-msvideo"; else->"application/octet-stream" }
                newChunkedResponse(Response.Status.OK,mime,input)
            } catch(e:Exception) { newFixedLengthResponse(Response.Status.INTERNAL_ERROR,"text/plain","Datei kann nicht gelesen werden") }
        }
        private fun findFile(parent:Uri,name:String):Uri? {
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(rootUri,DocumentsContract.getTreeDocumentId(parent))
            contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { c ->
                while(c.moveToNext()) {
                    val id=c.getString(0); val n=c.getString(1); val mime=c.getString(2)
                    val doc=DocumentsContract.buildDocumentUriUsingTree(rootUri,id)
                    if(mime==DocumentsContract.Document.MIME_TYPE_DIR) { val found=findFile(doc,name); if(found!=null)return found }
                    else if(n==name) return doc
                }
            }
            return null
        }
        private fun page():String {
            val videos=mutableListOf<String>(); listFiles(rootUri,videos)
            val items=videos.joinToString("") { n ->
                val safe=n.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                "<li><a href=\"/video/${java.net.URLEncoder.encode(n,"UTF-8")}\">$safe</a></li>"
            }
            return """<!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>USB-Cinema</title><style>body{font:18px system-ui;background:#10131b;color:#fff;max-width:850px;margin:2rem auto;padding:1rem}h1{color:#9bbcff}li{margin:1rem 0}a{color:#a7e6c1}</style><h1>USB-Cinema</h1><p>Filme auf diesem USB-Stick</p><ul>$items</ul></html>"""
        }
        private fun listFiles(parent:Uri,out:MutableList<String>) {
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(rootUri,DocumentsContract.getDocumentId(parent))
            contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { c ->
                while(c.moveToNext()) {
                    val id=c.getString(0); val n=c.getString(1); val mime=c.getString(2)
                    if(mime==DocumentsContract.Document.MIME_TYPE_DIR) listFiles(DocumentsContract.buildDocumentUriUsingTree(rootUri,id),out)
                    else if(n.substringAfterLast('.',"").lowercase(Locale.ROOT) in setOf("mp4","m4v","mkv","webm","mov","avi")) out.add(n)
                }
            }
        }
    }
}
