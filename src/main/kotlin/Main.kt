import TokenTypes.*
import Labels.*
import guru.zoroark.lixy.*
import guru.zoroark.lixy.matchers.matches
import java.io.BufferedReader
import java.io.File

enum class TokenTypes : LixyTokenType {
    WORD, INT, DOUBLE, BOOLEAN, STRING_CONTENT, QUOTES, WHITESPACE,
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
            matches("\\d+") isToken INT
            matches("\\d+\\.\\d+") isToken DOUBLE
            matches("true|false") isToken BOOLEAN
            matches("[a-zA-Z_]+") isToken WORD
            matches("\\n") isToken WHITESPACE
            matches(".*//.*") isToken COMMENT
            matches("\\s*\\(\\s*") isToken PAREN_OPEN
            matches("\\s*\\)\\s*") isToken PAREN_CLOSE
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
    val parser: HashMap<ArrayList<String>, (List<String>, MutableList<String>, HashMap<String, String>) -> Pair<Boolean, String>> = hashMapOf()
//    print ("hello")
    parser[arrayListOf("WORD:print", "PAREN_OPEN", "QUOTES", "STRING_CONTENT", "QUOTES", "PAREN_CLOSE", "SEMICOLON")] =
        { _, string, _ ->
            print(string.joinToString(" ") { it })
            Pair(true, "")
        }

//    println("hello")
    parser[arrayListOf("WORD:println", "PAREN_OPEN", "QUOTES", "STRING_CONTENT", "QUOTES", "PAREN_CLOSE", "SEMICOLON")] =
        { _, string, _ ->
            println(string.joinToString(" ") { it })
            Pair(true, "")
        }

//    print (x)
    parser[arrayListOf("WORD:print", "PAREN_OPEN", "WORD", "PAREN_CLOSE", "SEMICOLON")] =
        { name, _, variables ->
            print(variables[name.joinToString(" ") { it }])
            Pair(true, "")
        }

//    println(x)
    parser[arrayListOf("WORD:println", "PAREN_OPEN", "WORD", "PAREN_CLOSE", "SEMICOLON")] =
        { name, _, variables ->
            println(variables[name.joinToString(" ") { it }])
            Pair(true, "")
        }

//    str x = "hello";
    parser[arrayListOf("WORD:str", "WHITESPACE", "WORD", "SET_OPERATOR", "QUOTES", "STRING_CONTENT", "QUOTES", "SEMICOLON")] =
        { name, value, variables ->
            variables[name[0]] = value[0]
            Pair(true, "")
        }

    parser[arrayListOf("WORD:int", "WHITESPACE", "WORD", "SET_OPERATOR", "INT", "SEMICOLON")] =
        { name, value, variables ->
            variables[name[0]] = value[0]
            Pair(true, "")
        }

    parser[arrayListOf("WORD:double", "WHITESPACE", "WORD", "SET_OPERATOR", "DOUBLE", "SEMICOLON")] =
        { name, value, variables ->
            variables[name[0]] = value[0]
            Pair(true, "")
        }

    parser[arrayListOf("WORD:bool", "WHITESPACE", "WORD", "SET_OPERATOR", "BOOLEAN", "SEMICOLON")] =
        { name, value, variables ->
            variables[name[0]] = value[0]
            Pair(true, "")
        }


    val bufferedReader: BufferedReader = File("src/main/kotlin/main.pigsh").bufferedReader()
    val inputString = bufferedReader.use { it.readText() }
    run(lexer, parser, inputString)
}

fun run(lexer: LixyLexer, parser: HashMap<ArrayList<String>, (List<String>, MutableList<String>, HashMap<String, String>) -> Pair<Boolean, String>>, s: String) {
    val tokens = lexer.tokenize(s = s)
    val variables: HashMap<String, String> = parse(tokens, parser)
    println("\n\nvariables: $variables")
}

fun parse(tokens: List<LixyToken>, parser: HashMap<ArrayList<String>, (List<String>, MutableList<String>, HashMap<String, String>) -> Pair<Boolean, String>>): HashMap<String, String> {
    val variables: HashMap<String, String> = HashMap()
    val savedWords: List<String> = listOf("print", "println", "str", "bool", "int", "double")
    for (key in parser.keys) {
        for ((tokenIndex, _) in tokens.withIndex()) {
            val variableNames: MutableList<String> = mutableListOf()
            val content: MutableList<String> = mutableListOf()
            for (i in 0 until key.size) {
                val newToken = tokens[tokenIndex + i]
                if (key[i] == "${newToken.tokenType}:${newToken.string}" || key[i] == "${newToken.tokenType}") {
                    if (newToken.tokenType == INT || newToken.tokenType == DOUBLE || newToken.tokenType == STRING_CONTENT || newToken.tokenType == BOOLEAN) content.add(newToken.string)
                    else if (newToken.tokenType == WORD) if (!savedWords.contains(newToken.string)) variableNames.add(newToken.string)
//                    println("${newToken.tokenType}:${newToken.string}, ${key[i]}")
                    if (i == key.size - 1) {
                        val result: Pair<Boolean, String>? = parser[key]?.invoke(
                            variableNames,
                            content,
                            variables,
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
    return variables
}