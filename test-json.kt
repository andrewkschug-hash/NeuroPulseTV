import org.json.JSONTokener
import org.json.JSONObject

fun main() {
    val raw = """[{"stream_id":"1","name":"Movie"}]"""
    val tokener = JSONTokener(raw)
    tokener.nextValue()
    println("ok")
}
