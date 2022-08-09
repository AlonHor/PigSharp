import TokenTypes.*
import Labels.*
import guru.zoroark.lixy.*
import guru.zoroark.lixy.matchers.matches
import java.io.BufferedReader
import java.io.File

enum class TokenTypes : LixyTokenType {
    WORD, INT, DOUBLE, STRING_CONTENT, QUOTES, WHITESPACE,
    COMMENT, PAREN_OPEN, PAREN_CLOSE, BRACE_OPEN, BRACE_CLOSE,
    BRACKET_OPEN, BRACKET_CLOSE, SEMICOLON, COMMA,
    MATH_OPERATOR, COMPARISON_OPERATOR, ASSIGNMENT_OPERATOR, SET_OPERATOR,
    LOGICAL_OPERATOR, UNARY_OPERATOR,
}

enum class Labels : LixyStateLabel {
    IN_STRING
}

fun main() {
    val lexer = lixy {
        default state {
            "\"" isToken QUOTES thenState IN_STRING
//            matches("\\s*[a-zA-Z]\\s*") isToken FUNCTION_CALL
            matches("[a-zA-Z_]+") isToken WORD
            matches("\\d+") isToken INT
            matches("\\d+\\.\\d+") isToken DOUBLE
            matches("\\n") isToken WHITESPACE
            matches(".*//.*") isToken COMMENT
            matches("\\s*\\(\\s*") isToken PAREN_OPEN
            matches("\\s*\\)\\s*") isToken PAREN_CLOSE
            matches("\\s*\\{\\s*") isToken BRACE_OPEN
            matches("\\s*}\\s*") isToken BRACE_CLOSE
            matches("\\s*\\[\\s*") isToken BRACKET_OPEN
            matches("\\s*]\\s*") isToken BRACKET_CLOSE
            matches("\\s*;\\s*") isToken SEMICOLON
            matches("\\s*,\\s*") isToken COMMA
            matches("\\s*!\\s*") isToken UNARY_OPERATOR
            matches("\\s*=\\s*") isToken SET_OPERATOR // =
            matches("\\s*((==)|(<=)|(>=)|(!=)|[<>])\\s*") isToken COMPARISON_OPERATOR // == < > <= >= !=
            matches("\\s*(\\+=|-=)\\s*") isToken ASSIGNMENT_OPERATOR // += -=
            // matches("\\s*(?<= |\\w)=(?= |\\w)\\s*") isToken SET_OPERATOR // =
            matches("\\s+(and|or)\\s+") isToken LOGICAL_OPERATOR // and or
            matches("[+\\-*/^%<>!&|]") isToken MATH_OPERATOR
            matches("\\s+") isToken WHITESPACE
        }
        IN_STRING state {
            // triple quotes to make it a raw string, so that we don't need to
            // escape everything
            matches("""(\\"|[^"])+""") isToken STRING_CONTENT
            "\"" isToken QUOTES thenState default
        }
    }
    val parser: HashMap<ArrayList<String>, (List<String>?, List<String>?, List<Int>?, List<Double>?, List<Boolean>?, HashMap<String, String>?) -> Pair<Boolean, String>> = hashMapOf()
//    print ("hello")
    parser[arrayListOf("WORD:print", "PAREN_OPEN", "QUOTES", "STRING_CONTENT", "QUOTES", "PAREN_CLOSE", "SEMICOLON")] =
        { _, string, _, _, _, _ ->
            print(string?.joinToString(" ") { it })
            Pair(true, "")
        }

//    println("hello")
    parser[arrayListOf("WORD:println", "PAREN_OPEN", "QUOTES", "STRING_CONTENT", "QUOTES", "PAREN_CLOSE", "SEMICOLON")] =
        { _, string, _, _, _, _ ->
            println(string?.joinToString(" ") { it })
            Pair(true, "")
        }

//    print (x)
    parser[arrayListOf("WORD:print", "PAREN_OPEN", "WORD", "PAREN_CLOSE", "SEMICOLON")] =
        { name, _, _, _, _, stringVariables ->
            print(stringVariables?.get(name?.joinToString(" ") { it }))
            Pair(true, "")
        }

//    println(x)
    parser[arrayListOf("WORD:println", "PAREN_OPEN", "WORD", "PAREN_CLOSE", "SEMICOLON")] =
        { name, _, _, _, _, stringVariables ->
            println(stringVariables?.get(name?.joinToString(" ") { it }))
            Pair(true, "")
        }

//    str x = "hello";
    parser[arrayListOf("WORD:str", "WHITESPACE", "WORD", "SET_OPERATOR", "QUOTES", "STRING_CONTENT", "QUOTES", "SEMICOLON")] =
        { name, value, _, _, _, stringVariables ->
            if (name != null) stringVariables?.set(name[0], value?.get(0).toString())
            Pair(true, "")
        }


    val bufferedReader: BufferedReader = File("src/main/kotlin/main.pigsh").bufferedReader()
    val inputString = bufferedReader.use { it.readText() }
    run(lexer, parser, inputString)
}

fun run(lexer: LixyLexer, parser: HashMap<ArrayList<String>, (List<String>?, List<String>?, List<Int>?, List<Double>?, List<Boolean>?, HashMap<String, String>?) -> Pair<Boolean, String>>, s: String) {
    val tokens = lexer.tokenize(s = s)
    val variables: HashMap<String, String> = parse(tokens, parser)
    println("\n\nvariables: $variables")
}

fun parse(tokens: List<LixyToken>, parser: HashMap<ArrayList<String>, (List<String>?, List<String>?, List<Int>?, List<Double>?, List<Boolean>?, HashMap<String, String>?) -> Pair<Boolean, String>>): HashMap<String, String> {
    val stringVariables: HashMap<String, String> = HashMap()
    val savedWords: List<String> = listOf("print", "str")
    for (key in parser.keys) {
        for ((tokenIndex, _) in tokens.withIndex()) {
            val variableNames: MutableList<String> = mutableListOf()
            val stringContent: MutableList<String> = mutableListOf()
            val intContent: MutableList<Int> = mutableListOf()
            val doubleContent: MutableList<Double> = mutableListOf()
            val booleanContent: MutableList<Boolean> = mutableListOf()
            for (i in 0 until key.size) {
                val newToken = tokens[tokenIndex + i]
                if (key[i] == "${newToken.tokenType}:${newToken.string}" || key[i] == "${newToken.tokenType}") {
                    if (newToken.tokenType == STRING_CONTENT) stringContent.add(newToken.string)
                    if (newToken.tokenType == WORD && !savedWords.contains(newToken.string)) variableNames.add(newToken.string)
                    if (newToken.tokenType == INT) intContent.add(newToken.string.toInt())
                    if (newToken.tokenType == DOUBLE) doubleContent.add(newToken.string.toDouble())
//                    println("${newToken.tokenType}:${newToken.string}, ${key[i]}")
                    if (i == key.size - 1) {
                        val result: Pair<Boolean, String>? = parser[key]?.invoke(
                            variableNames,
                            stringContent,
                            intContent,
                            doubleContent,
                            booleanContent,
                            stringVariables
                        )
                        if (result?.first == false) println("Error: ${result.second}")
                    }
                } else {
//                    print("Error: ${newToken.tokenType}:${newToken.string}, ${key[i]}")
                    break
                }
            }
        }
    }
    return stringVariables
}