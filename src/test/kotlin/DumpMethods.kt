import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR

fun main() {
    println("CTP methods containing 'drawing' or 'Drawing':")
    CTP::class.java.methods.filter { it.name.contains("drawing", ignoreCase = true) }.forEach { println(it.name) }
    
    println("\nCTR methods containing 'drawing' or 'Drawing':")
    CTR::class.java.methods.filter { it.name.contains("drawing", ignoreCase = true) }.forEach { println(it.name) }
    
    println("\nCTR methods containing 'pict' or 'Pict':")
    CTR::class.java.methods.filter { it.name.contains("pict", ignoreCase = true) }.forEach { println(it.name) }
}
